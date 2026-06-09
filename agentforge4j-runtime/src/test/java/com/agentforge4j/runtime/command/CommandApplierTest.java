package com.agentforge4j.runtime.command;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.RunCommandCommand;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CommandApplierTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void constructor_rejectsEmptyHandlerList() {
    assertThatThrownBy(() -> new CommandApplier(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commandHandlers must not be empty");
  }

  @Test
  void constructor_rejectsDuplicateCommandClasses() {
    EventRecorder r1 = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
    EventRecorder r2 = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
    assertThatThrownBy(() -> new CommandApplier(List.of(
        new CompleteCommandHandler(r1),
        new CompleteCommandHandler(r2))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate CommandHandler")
        .hasMessageContaining("CompleteCommand");
  }

  @Test
  void apply_throwsWhenNoHandlerRegisteredForCommandType() {
    EventRecorder recorder = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
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
    CommandApplier applier = applier();
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
    CommandApplier applier = applier();
    assertThatThrownBy(() -> applier.apply(null, stateAtStep("s1"), ContextMapping.none(), "a", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commands must not be null");
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

  private static CommandApplier applier() {
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
    FileSink fileSink = mock(FileSink.class);
    ShellCommandRunner shell = mock(ShellCommandRunner.class);
    return new CommandApplier(determineCommandHandlers(eventRecorder, fileSink, shell, CLOCK));
  }

  private static List<CommandHandler<? extends LlmCommand>> determineCommandHandlers(
      EventRecorder eventRecorder, FileSink resolvedFileSink, ShellCommandRunner resolvedShell,
      Clock resolvedClock) {
    return List.of(
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
