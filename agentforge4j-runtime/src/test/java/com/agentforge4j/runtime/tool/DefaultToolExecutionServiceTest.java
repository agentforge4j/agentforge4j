// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolExecutionOutcome;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.core.spi.tool.ToolSourceKind;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.runtime.event.EventRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolExecutionServiceTest {

  private static final String SCHEMA = "{\"type\":\"object\",\"required\":[\"title\"]}";

  private final CapturingEventLog eventLog = new CapturingEventLog();
  private final InMemoryPendingToolInvocationStore store = new InMemoryPendingToolInvocationStore();
  private final ScriptedProvider provider = new ScriptedProvider();
  private final EventRecorder eventRecorder = new EventRecorder(eventLog,
          Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC));

  private final ToolInvocationContext ctx =
      new ToolInvocationContext("run-1", "7", "agent-1", new ToolScope("wf-1", "run-1"));

  private DefaultToolExecutionService service(ToolPolicy policy) {
    return serviceWithOptions(policy, ToolExecutionOptions.defaults());
  }

  /** The single pre-built provider feeds the resolver directly, so the factory is never called. */
  private static ToolProviderFactory unusedFactory() {
    return definition -> {
      throw new AssertionError("factory must not be called for pre-built providers");
    };
  }

  private DefaultToolExecutionService serviceWithOptions(ToolPolicy policy,
      ToolExecutionOptions options) {
    return new DefaultToolExecutionService(
        new IntegrationToolProviderResolver(new InMemoryIntegrationRepository(),
            unusedFactory(), List.of(provider)),
        policy,
        store,
        options,
        eventRecorder,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void allowExecutesAndEmitsRequestedThenCompleted() {
    provider.result = ToolResult.success("{\"ok\":true}", 5L);

    ToolExecutionOutcome outcome = service(allow()).execute(command(Map.of("title", "x")), ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.EXECUTED);
    assertThat(outcome.result().output()).isEqualTo("{\"ok\":true}");
    assertThat(eventTypes()).containsExactly(
        WorkflowEventType.TOOL_INVOCATION_REQUESTED, WorkflowEventType.TOOL_INVOCATION_COMPLETED);
  }

  @Test
  void denyReturnsDeniedAndEmitsDenied() {
    ToolExecutionOutcome outcome = service(deny("nope")).execute(command(Map.of("title", "x")),
        ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.DENIED);
    assertThat(eventTypes()).contains(WorkflowEventType.TOOL_INVOCATION_DENIED);
  }

  @Test
  void requireApprovalPersistsPendingAndReturnsApprovalPending() {
    ToolInvocationCommand command = command(Map.of("title", "x"));

    ToolExecutionOutcome outcome = service(requireApproval()).execute(command, ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.APPROVAL_PENDING);
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
    assertThat(eventTypes()).contains(WorkflowEventType.TOOL_INVOCATION_APPROVAL_PENDING);
  }

  @Test
  void unknownCapabilityFailsAtResolve() {
    ToolExecutionOutcome outcome =
        service(allow()).execute(
            new ToolInvocationCommand(null, "unknown.capability", Map.of(), null), ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.FAILED);
    assertThat(failedPhase()).isEqualTo("RESOLVE");
  }

  @Test
  void missingRequiredArgumentFailsAtValidateWithoutPolicy() {
    ToolExecutionOutcome outcome = service(deny("should-not-run")).execute(command(Map.of()), ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.FAILED);
    assertThat(failedPhase()).isEqualTo("VALIDATE");
    assertThat(eventTypes()).doesNotContain(WorkflowEventType.TOOL_INVOCATION_DENIED);
  }

  @Test
  void providerErrorFailsAtInvoke() {
    provider.result = ToolResult.failure("remote boom", 3L);

    ToolExecutionOutcome outcome = service(allow()).execute(command(Map.of("title", "x")), ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.FAILED);
    assertThat(failedPhase()).isEqualTo("INVOKE");
    assertThat(eventTypes()).doesNotContain(WorkflowEventType.TOOL_INVOCATION_COMPLETED);
  }

  @Test
  void resumeApproveInvokesStoredCallAndRemovesPending() {
    DefaultToolExecutionService service = service(requireApproval());
    ToolInvocationCommand command = command(Map.of("title", "x"));
    service.execute(command, ctx);
    provider.result = ToolResult.success("{\"done\":true}", 4L);

    ToolExecutionOutcome outcome =
        service.resume("run-1", command.toolInvocationId(), new ApprovalDecision.Approve("alice"));

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.EXECUTED);
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();
    assertThat(eventTypes()).contains(WorkflowEventType.TOOL_INVOCATION_COMPLETED);
  }

  @Test
  void resumeRejectDeniesAndRemovesPending() {
    DefaultToolExecutionService service = service(requireApproval());
    ToolInvocationCommand command = command(Map.of("title", "x"));
    service.execute(command, ctx);

    ToolExecutionOutcome outcome = service.resume("run-1", command.toolInvocationId(),
        new ApprovalDecision.Reject("bob", "not allowed"));

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.DENIED);
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();
  }

  @Test
  void resumeWithUnknownIdFails() {
    ToolExecutionOutcome outcome =
        service(allow()).resume("run-1", "missing", new ApprovalDecision.Approve("alice"));

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.FAILED);
  }

  @Test
  void invokeFailureRetriesUpToMaxRetriesThenSucceeds() {
    provider.scriptedResults.add(ToolResult.failure("boom-1", 1L));
    provider.scriptedResults.add(ToolResult.failure("boom-2", 1L));
    provider.scriptedResults.add(ToolResult.success("{\"ok\":true}", 1L));
    ToolExecutionOptions options = new ToolExecutionOptions(Duration.ofSeconds(30), 2,
        Duration.ZERO);

    ToolExecutionOutcome outcome =
        serviceWithOptions(allow(), options).execute(command(Map.of("title", "x")), ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.EXECUTED);
    assertThat(provider.invocations).isEqualTo(3);
  }

  @Test
  void invokeFailureExhaustsRetriesAndSuspendsForDecision() {
    provider.result = ToolResult.failure("always", 1L);
    ToolExecutionOptions options = new ToolExecutionOptions(Duration.ofSeconds(30), 1,
        Duration.ZERO);
    ToolInvocationCommand command = command(Map.of("title", "x"));

    ToolExecutionOutcome outcome = serviceWithOptions(allow(), options).execute(command, ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.FAILED);
    assertThat(provider.invocations).isEqualTo(2);
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
  }

  @Test
  void resolveAndValidateFailuresAreNotRetried() {
    provider.result = ToolResult.success("{\"ok\":true}", 1L);
    ToolExecutionOptions options = new ToolExecutionOptions(Duration.ofSeconds(30), 3,
        Duration.ZERO);

    serviceWithOptions(allow(), options).execute(command(Map.of()), ctx);

    // Missing required 'title' fails at VALIDATE before any invoke; retries never apply.
    assertThat(provider.invocations).isZero();
  }

  @Test
  void denySuspendsForDecisionWithPendingRow() {
    ToolInvocationCommand command = command(Map.of("title", "x"));

    ToolExecutionOutcome outcome = service(deny("nope")).execute(command, ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.DENIED);
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
  }

  // --- risk signal (ToolRiskMetadata) ---------------------------------------------------------

  @Test
  void riskSignalIsAvailableToPolicyOnTheResolvedDescriptor() {
    provider.result = ToolResult.success("{\"ok\":true}", 1L);
    AtomicReference<ToolRiskMetadata> seen = new AtomicReference<>();
    ToolPolicy capturing = (cmd, descriptor, cit) -> {
      seen.set(descriptor.riskMetadata());
      return new PolicyDecision.Allow();
    };

    service(capturing).execute(command(Map.of("title", "x")), ctx);

    assertThat(seen.get()).isNotNull();
    assertThat(seen.get().mutating()).isTrue();
  }

  @Test
  void allowAllPolicyIgnoresRiskSignalAndExecutes() {
    provider.result = ToolResult.success("{\"ok\":true}", 1L);

    ToolExecutionOutcome outcome =
        service(ToolPolicy.allowAll()).execute(command(Map.of("title", "x")), ctx);

    // The conservative (mutating) signal is present but the allow-all policy does not consume it.
    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.EXECUTED);
  }

  @Test
  void riskAwarePolicyMayRaiseApprovalOnAMutatingSignal() {
    ToolPolicy riskAware = (cmd, descriptor, cit) -> descriptor.riskMetadata().mutating()
        ? new PolicyDecision.RequireApproval("mutating tool", "OPERATOR")
        : new PolicyDecision.Allow();

    ToolExecutionOutcome outcome =
        service(riskAware).execute(command(Map.of("title", "x")), ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.APPROVAL_PENDING);
  }

  @Test
  void policyIsAuthoritativeSoAFalseSignalCannotBypassARequiredApproval() {
    provider.risk = new ToolRiskMetadata(false);

    ToolExecutionOutcome outcome =
        service(requireApproval()).execute(command(Map.of("title", "x")), ctx);

    // mutating=false is advisory; it must not reduce a policy-required approval.
    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.APPROVAL_PENDING);
  }

  private ToolInvocationCommand command(Map<String, Object> arguments) {
    return new ToolInvocationCommand(null, "github.create_pull_request", arguments, "because");
  }

  private List<WorkflowEventType> eventTypes() {
    List<WorkflowEventType> types = new ArrayList<>();
    for (WorkflowEvent event : eventLog.events) {
      types.add(event.eventType());
    }
    return types;
  }

  private String failedPhase() {
    return eventLog.events.stream()
        .filter(event -> event.eventType() == WorkflowEventType.TOOL_INVOCATION_FAILED)
        .map(WorkflowEvent::payload)
        .findFirst()
        .map(payload -> payload.contains("\"phase\":\"RESOLVE\"") ? "RESOLVE"
            : payload.contains("\"phase\":\"VALIDATE\"") ? "VALIDATE"
                : payload.contains("\"phase\":\"INVOKE\"") ? "INVOKE" : "UNKNOWN")
        .orElse("NONE");
  }

  private static ToolPolicy allow() {
    return (cmd, descriptor, cit) -> new PolicyDecision.Allow();
  }

  private static ToolPolicy deny(String reason) {
    return (cmd, descriptor, cit) -> new PolicyDecision.Deny(reason);
  }

  private static ToolPolicy requireApproval() {
    return (cmd, descriptor, cit) -> new PolicyDecision.RequireApproval("needs review", "OPERATOR");
  }

  private static final class ScriptedProvider implements ToolProvider {

    private ToolResult result = ToolResult.success(null, 0L);
    private final Deque<ToolResult> scriptedResults = new ArrayDeque<>();
    private ToolRiskMetadata risk = ToolRiskMetadata.conservative();
    private int invocations;

    @Override
    public String providerId() {
      return "mcp:test";
    }

    @Override
    public List<ToolDescriptor> listTools() {
      return List.of(
          new ToolDescriptor("github.create_pull_request", "Create PR", null, SCHEMA, null,
              new ToolSource("mcp:test", "create_pull_request", ToolSourceKind.REMOTE_HTTP), risk));
    }

    @Override
    public ToolResult invoke(ToolDescriptor descriptor, String arguments,
        ToolInvocationContext ctx, ToolExecutionOptions options) {
      invocations++;
      return scriptedResults.isEmpty() ? result : scriptedResults.poll();
    }

    @Override
    public HealthStatus health() {
      return new HealthStatus(HealthStatus.State.UP, null);
    }
  }

  private static final class CapturingEventLog implements WorkflowEventLog {

    private final List<WorkflowEvent> events = new ArrayList<>();

    @Override
    public void append(WorkflowEvent event) {
      events.add(event);
    }

    @Override
    public List<WorkflowEvent> getEvents(String runId) {
      return List.copyOf(events);
    }
  }
}
