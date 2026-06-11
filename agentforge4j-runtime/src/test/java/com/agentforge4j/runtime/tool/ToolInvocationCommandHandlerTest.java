package com.agentforge4j.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolInvocationCommandHandlerTest {

  private static final String SCHEMA = "{\"type\":\"object\",\"required\":[\"title\"]}";
  private static final String CAPABILITY = "github.create_pull_request";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC);

  private final InMemoryPendingToolInvocationStore store = new InMemoryPendingToolInvocationStore();
  private final ScriptedProvider provider = new ScriptedProvider();
  private final EventRecorder eventRecorder =
      new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);

  private final WorkflowState state = state();

  @Test
  void executedWritesContextAndContinues() {
    provider.result = ToolResult.success("{\"url\":\"http://pr/1\"}", 4L);

    CommandApplicationResult result = handler(allow()).apply(command(Map.of("title", "x")), request());

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    assertThat(state.getContextValue("tool." + CAPABILITY)).isPresent();
  }

  @Test
  void requireApprovalSuspendsForToolApprovalAndPersistsPending() {
    ToolInvocationCommand command = command(Map.of("title", "x"));

    CommandApplicationResult result = handler(requireApproval()).apply(command, request());

    assertThat(result).isEqualTo(CommandApplicationResult.AWAITING_TOOL_APPROVAL);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.AWAITING_TOOL_APPROVAL);
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
  }

  @Test
  void deniedSuspendsForToolDecision() {
    ToolInvocationCommand command = command(Map.of("title", "x"));

    CommandApplicationResult result = handler(deny("nope")).apply(command, request());

    assertThat(result).isEqualTo(CommandApplicationResult.AWAITING_TOOL_DECISION);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.AWAITING_TOOL_DECISION);
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
  }

  @Test
  void invokeFailureSuspendsForToolDecision() {
    provider.result = ToolResult.failure("remote boom", 3L);
    ToolInvocationCommand command = command(Map.of("title", "x"));

    CommandApplicationResult result = handler(allow()).apply(command, request());

    assertThat(result).isEqualTo(CommandApplicationResult.AWAITING_TOOL_DECISION);
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
    assertThat(provider.invocations).isEqualTo(1);
  }

  @Test
  void resolveFailureSuspendsForToolDecisionWithoutInvoking() {
    ToolInvocationCommand command =
        new ToolInvocationCommand(null, "unknown.capability", Map.of(), null);

    CommandApplicationResult result = handler(allow()).apply(command, request());

    assertThat(result).isEqualTo(CommandApplicationResult.AWAITING_TOOL_DECISION);
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
    assertThat(provider.invocations).isZero();
  }

  @Test
  void validateFailureSuspendsForToolDecisionWithoutInvoking() {
    ToolInvocationCommand command = command(Map.of());

    CommandApplicationResult result = handler(allow()).apply(command, request());

    assertThat(result).isEqualTo(CommandApplicationResult.AWAITING_TOOL_DECISION);
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
    assertThat(provider.invocations).isZero();
  }

  /** The single pre-built provider feeds the resolver directly, so the factory is never called. */
  private static ToolProviderFactory unusedFactory() {
    return definition -> {
      throw new AssertionError("factory must not be called for pre-built providers");
    };
  }

  private ToolInvocationCommandHandler handler(ToolPolicy policy) {
    DefaultToolExecutionService service = new DefaultToolExecutionService(
        new IntegrationToolProviderResolver(new InMemoryIntegrationRepository(),
            unusedFactory(), List.of(provider)),
        policy,
        store,
        new ToolExecutionOptions(Duration.ofSeconds(30), 0, Duration.ZERO),
        eventRecorder,
        new ObjectMapper(),
        CLOCK);
    return new ToolInvocationCommandHandler(service, new ToolResultApplier(eventRecorder), CLOCK);
  }

  private CommandApplicationRequest request() {
    return new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 7);
  }

  private static ToolInvocationCommand command(Map<String, Object> arguments) {
    return new ToolInvocationCommand(null, CAPABILITY, arguments, "because");
  }

  private static WorkflowState state() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    state.setCurrentStepId("s1");
    state.putStepExecutionUid("s1", 7);
    return state;
  }

  private static ToolPolicy allow() {
    return (cmd, descriptor, ctx) -> new PolicyDecision.Allow();
  }

  private static ToolPolicy deny(String reason) {
    return (cmd, descriptor, ctx) -> new PolicyDecision.Deny(reason);
  }

  private static ToolPolicy requireApproval() {
    return (cmd, descriptor, ctx) -> new PolicyDecision.RequireApproval("needs review", "OPERATOR");
  }

  private static final class ScriptedProvider implements ToolProvider {

    private ToolResult result = ToolResult.success("{\"ok\":true}", 1L);
    private int invocations;

    @Override
    public String providerId() {
      return "mcp:test";
    }

    @Override
    public List<ToolDescriptor> listTools() {
      return List.of(new ToolDescriptor(CAPABILITY, "Create PR", null, SCHEMA, null,
          new ToolSource("mcp:test", "create_pull_request")));
    }

    @Override
    public ToolResult invoke(ToolDescriptor descriptor, String arguments,
        ToolInvocationContext ctx, ToolExecutionOptions options) {
      invocations++;
      return result;
    }

    @Override
    public HealthStatus health() {
      return new HealthStatus(HealthStatus.State.UP, null);
    }
  }
}
