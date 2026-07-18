// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolExecutionOutcome;
import com.agentforge4j.core.spi.tool.ToolInvocationClaimLostException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(value = 30)
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
  void resumeWithUnknownIdThrowsTheTypedClaimLostSignalRatherThanAToolFailure() {
    assertThatThrownBy(() ->
        service(allow()).resume("run-1", "missing", new ApprovalDecision.Approve("alice")))
        .isInstanceOf(ToolInvocationClaimLostException.class);
  }

  /**
   * Regression for the stale-peek race in {@code resume()}'s POLICY_DENIED terminal check: an
   * {@code Approve} racing a concurrent {@code Reject} on the same policy-denied row must never
   * emit an override-rejected event or throw {@code PolicyDenialTerminalException} once the
   * {@code Reject} has already resolved the row — it must observe the row is gone and report a lost
   * claim instead. Deterministic, no sleeps: a wrapping store delays the {@code Approve} thread's
   * {@code verifyStillCurrent} call (the atomic re-check this fix introduces) until the main thread's
   * {@code Reject} call has fully applied and removed the row.
   */
  @Test
  void concurrentRejectResolvingAPolicyDeniedRowNeverLetsARacingApproveEmitAStaleOverrideRejectedEvent()
      throws InterruptedException {
    ToolInvocationCommand command = command(Map.of("title", "x"));
    CountDownLatch verifyEntered = new CountDownLatch(1);
    CountDownLatch rejectApplied = new CountDownLatch(1);
    VerifyDelayingStore delayingStore = new VerifyDelayingStore(store, verifyEntered, rejectApplied);
    DefaultToolExecutionService service = new DefaultToolExecutionService(
        new IntegrationToolProviderResolver(new InMemoryIntegrationRepository(),
            unusedFactory(), List.of(provider)),
        deny("nope"),
        delayingStore,
        ToolExecutionOptions.defaults(),
        eventRecorder,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC));
    service.execute(command, ctx);

    AtomicReference<Throwable> approveError = new AtomicReference<>();
    Thread approveThread = new Thread(() -> {
      try {
        service.resume("run-1", command.toolInvocationId(), new ApprovalDecision.Approve("alice"));
      } catch (Throwable t) {
        approveError.set(t);
      }
    });
    approveThread.start();
    try {
      assertThat(verifyEntered.await(10, TimeUnit.SECONDS))
          .as("verifyStillCurrent() was never entered - the stale-peek re-check regressed")
          .isTrue();

      ToolExecutionOutcome rejectOutcome = service.resume("run-1", command.toolInvocationId(),
          new ApprovalDecision.Reject("bob", "not allowed"));
      assertThat(rejectOutcome.status()).isEqualTo(ToolExecutionOutcome.Status.DENIED);

      rejectApplied.countDown();
      approveThread.join();

      assertThat(approveError.get()).isInstanceOf(ToolInvocationClaimLostException.class);
      assertThat(eventTypes()).doesNotContain(WorkflowEventType.TOOL_INVOCATION_OVERRIDE_REJECTED);
      assertThat(store.find("run-1", command.toolInvocationId())).isNull();
    } finally {
      rejectApplied.countDown();
      approveThread.join();
    }
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

  /**
   * Delays the first {@link #verifyStillCurrent} call — signalling entry, then blocking until
   * released — so a test can force a concurrent claim to fully apply in the gap between an
   * {@code Approve} thread's initial peek and its atomic re-check. Every other method delegates
   * immediately.
   */
  private static final class VerifyDelayingStore implements PendingToolInvocationStore {

    private final PendingToolInvocationStore delegate;
    private final CountDownLatch verifyEntered;
    private final CountDownLatch proceed;

    VerifyDelayingStore(PendingToolInvocationStore delegate, CountDownLatch verifyEntered,
        CountDownLatch proceed) {
      this.delegate = delegate;
      this.verifyEntered = verifyEntered;
      this.proceed = proceed;
    }

    @Override
    public void save(PendingToolInvocation pending) {
      delegate.save(pending);
    }

    @Override
    public PendingToolInvocation find(String runId, String toolInvocationId) {
      return delegate.find(runId, toolInvocationId);
    }

    @Override
    public List<PendingToolInvocation> findByRun(String runId) {
      return delegate.findByRun(runId);
    }

    @Override
    public void remove(String runId, String toolInvocationId) {
      delegate.remove(runId, toolInvocationId);
    }

    @Override
    public PendingToolInvocation claim(String runId, String toolInvocationId,
        PendingToolInvocation expectedPending) {
      return delegate.claim(runId, toolInvocationId, expectedPending);
    }

    @Override
    public PendingToolInvocation verifyStillCurrent(String runId, String toolInvocationId,
        PendingToolInvocation expectedPending) {
      verifyEntered.countDown();
      try {
        proceed.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return delegate.verifyStillCurrent(runId, toolInvocationId, expectedPending);
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
