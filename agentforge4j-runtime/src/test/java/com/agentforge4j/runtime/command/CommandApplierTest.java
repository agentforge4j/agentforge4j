package com.agentforge4j.runtime.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentforge4j.core.command.CallEndpointCommand;
import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.RunCommandCommand;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.integrations.AgentIntegration;
import com.agentforge4j.integrations.IntegrationRegistry;
import com.agentforge4j.runtime.command.handler.CallEndpointCommandHandler;
import com.agentforge4j.runtime.command.handler.CompleteCommandHandler;
import com.agentforge4j.runtime.command.handler.ContinueCommandHandler;
import com.agentforge4j.runtime.command.handler.CreateFileCommandHandler;
import com.agentforge4j.runtime.command.handler.EscalateCommandHandler;
import com.agentforge4j.runtime.command.handler.GeneralQuestionCommandHandler;
import com.agentforge4j.runtime.command.handler.RunCommandHandler;
import com.agentforge4j.runtime.command.handler.SetContextCommandHandler;
import com.agentforge4j.runtime.command.handler.UserPromptCommandHandler;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommandApplierTest {

  @Test
  void constructor_rejectsEmptyHandlerList() {
    assertThatThrownBy(() -> new CommandApplier(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commandHandlers must not be empty");
  }

  @Test
  void constructor_rejectsDuplicateCommandClasses() {
    InMemoryWorkflowEventLog log = new InMemoryWorkflowEventLog();
    EventRecorder r1 = new EventRecorder(log,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    EventRecorder r2 = new EventRecorder(new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    assertThatThrownBy(() -> new CommandApplier(List.of(
        new CompleteCommandHandler(r1),
        new CompleteCommandHandler(r2))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate CommandHandler")
        .hasMessageContaining("CompleteCommand");
  }

  @Test
  void apply_throwsWhenNoHandlerRegisteredForCommandType() {
    InMemoryWorkflowEventLog log = new InMemoryWorkflowEventLog();
    EventRecorder recorder = new EventRecorder(log,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    CommandApplier applier = new CommandApplier(List.of(new CompleteCommandHandler(recorder)));
    WorkflowState state = stateAtStep("s1");
    ContextMapping mapping = ContextMapping.none();

    assertThatThrownBy(() -> applier.apply(
        List.of(new RunCommandCommand("echo hi")), state, mapping, "agent-1", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No CommandHandler registered")
        .hasMessageContaining("RunCommandCommand");
  }

  @Test
  void apply_returnsFirstNonContinueOutcome() {
    CommandApplier applier = applierWithIntegration("int1", "op1", "resp-body");
    WorkflowState state = stateAtStep("s1");
    ContextMapping mapping = ContextMapping.none();

    CommandApplicationResult result = applier.apply(
        List.of(
            new ContinueCommand(null, null, null),
            new CompleteCommand("done")),
        state,
        mapping,
        "agent-1",
        1);

    assertThat(result).isEqualTo(CommandApplicationResult.COMPLETE_SIGNAL);
  }

  @Test
  void apply_rejectsNullCommands() {
    CommandApplier applier = applierWithIntegration("int1", "op1", "x");
    assertThatThrownBy(() -> applier.apply(null, stateAtStep("s1"), ContextMapping.none(), "a", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commands must not be null");
  }

  @Test
  void callEndpoint_throwsWhenIntegrationDisabled() {
    AgentIntegration integration = mock(AgentIntegration.class);
    when(integration.integrationId()).thenReturn("int1");
    when(integration.execute(any(), any())).thenReturn("should-not-run");

    IntegrationRegistry registry = mock(IntegrationRegistry.class);
    when(registry.isEnabled("int1")).thenReturn(false);

    EventRecorder eventRecorder = new EventRecorder(
        new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    FileSink fileSink = mock(FileSink.class);
    ShellCommandRunner shell = mock(ShellCommandRunner.class);

    CommandApplier applier = new CommandApplier(
        determineCommandHandlers(eventRecorder, fileSink, shell,
            Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC), registry));

    WorkflowState state = stateAtStep("s1");
    CallEndpointCommand cmd = new CallEndpointCommand("int1", "op1", Map.of(), null);

    assertThatThrownBy(() -> applier.apply(List.of(cmd), state, ContextMapping.none(), "agent-1", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not configured or not enabled");
  }

  @Test
  void callEndpoint_writes_when_context_key_declared_in_output_keys() {
    CommandApplier applier = applierWithIntegration("int1", "op1", "resp-body");
    WorkflowState state = stateAtStep("s1");
    ContextMapping mapping = new ContextMapping(List.of(), List.of("api.out"));
    CallEndpointCommand cmd = new CallEndpointCommand("int1", "op1", Map.of(), "api.out");

    CommandApplicationResult result = applier.apply(List.of(cmd), state, mapping, "agent-1", 3);

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    assertThat(state.getContextValue("api.out"))
        .contains(new StringContextValue("resp-body"));
    assertThat(state.getContextKeyWrittenAtUid().get("api.out")).isEqualTo(3);
  }

  @Test
  void callEndpoint_rejects_context_key_not_in_output_keys() {
    CommandApplier applier = applierWithIntegration("int1", "op1", "x");
    WorkflowState state = stateAtStep("s1");
    ContextMapping mapping = new ContextMapping(List.of(), List.of("allowed-only"));
    CallEndpointCommand cmd = new CallEndpointCommand("int1", "op1", Map.of(), "secret-key");

    assertThatThrownBy(() -> applier.apply(List.of(cmd), state, mapping, "agent-1", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("secret-key")
        .hasMessageContaining("outputKeys");
  }

  private static WorkflowState stateAtStep(String stepId) {
    WorkflowState state = new WorkflowState(
        "run-1",
        "wf-1",
        null,
        Instant.parse("2026-05-01T00:00:00Z"));
    state.setCurrentStepId(stepId);
    return state;
  }

  private static CommandApplier applierWithIntegration(String integrationId,
      String operation,
      String responseBody) {
    AgentIntegration integration = mock(AgentIntegration.class);
    when(integration.integrationId()).thenReturn(integrationId);
    when(integration.execute(eq(operation), any())).thenReturn(responseBody);

    IntegrationRegistry registry = mock(IntegrationRegistry.class);
    when(registry.isEnabled(integrationId)).thenReturn(true);
    when(registry.isOperationAllowed(integrationId, operation)).thenReturn(true);
    when(registry.resolve(integrationId)).thenReturn(Optional.of(integration));

    EventRecorder eventRecorder = new EventRecorder(
        new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));

    FileSink fileSink = mock(FileSink.class);
    ShellCommandRunner shell = mock(ShellCommandRunner.class);

    return new CommandApplier(
        determineCommandHandlers(eventRecorder, fileSink, shell,
            Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC), registry));
  }

  private static List<CommandHandler<? extends LlmCommand>> determineCommandHandlers(
      EventRecorder eventRecorder, FileSink resolvedFileSink, ShellCommandRunner resolvedShell,
      Clock resolvedClock, IntegrationRegistry resolvedRegistry) {
    return List.of(new CallEndpointCommandHandler(eventRecorder, resolvedRegistry),
        new CompleteCommandHandler(eventRecorder),
        new ContinueCommandHandler(),
        new CreateFileCommandHandler(eventRecorder, resolvedFileSink),
        new EscalateCommandHandler(eventRecorder, resolvedClock),
        new GeneralQuestionCommandHandler(eventRecorder, resolvedClock),
        new RunCommandHandler(eventRecorder, resolvedShell),
        new SetContextCommandHandler(eventRecorder),
        new UserPromptCommandHandler(eventRecorder, resolvedClock));
  }
}
