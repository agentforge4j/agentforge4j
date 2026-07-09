// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.RequestContextCommand;
import com.agentforge4j.core.command.RunCommandCommand;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import java.util.ArrayList;
import java.util.Map;
import com.agentforge4j.runtime.InMemoryGeneratedArtifactStore;
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
        List.of(new RunCommandCommand("echo hi")), state, mapping, "agent-1", 1, step("s1"),
        workflow()))
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
        1,
        step("s1"),
        workflow());

    assertThat(result).isEqualTo(CommandApplicationResult.COMPLETE_SIGNAL);
  }

  @Test
  void apply_rejectsNullCommands() {
    CommandApplier applier = applier();
    assertThatThrownBy(() -> applier.apply(null, stateAtStep("s1"), ContextMapping.none(), "a", 1,
        step("s1"), workflow()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commands must not be null");
  }

  @Test
  void apply_countsPriorExpansionsPerSelectorAndOnlyForRequestContextCommands() {
    // CommandApplicationRequest documents priorRequestContextExpansions as the number of selectors
    // requested by EARLIER RequestContextCommands in the batch (0 for any other command type — a
    // non-RCC command after an RCC must not inherit the counter). Selectors are counted, not
    // commands, so a two-selector command advances the counter by two.
    List<Integer> continuePriors = new ArrayList<>();
    List<Integer> requestContextPriors = new ArrayList<>();
    CommandHandler<ContinueCommand> capturingContinue = new CommandHandler<>() {
      @Override
      public Class<ContinueCommand> getCommandClass() {
        return ContinueCommand.class;
      }

      @Override
      public CommandApplicationResult apply(ContinueCommand command,
          CommandApplicationRequest request) {
        continuePriors.add(request.priorRequestContextExpansions());
        return CommandApplicationResult.CONTINUE;
      }
    };
    CommandHandler<RequestContextCommand> capturingRequestContext = new CommandHandler<>() {
      @Override
      public Class<RequestContextCommand> getCommandClass() {
        return RequestContextCommand.class;
      }

      @Override
      public CommandApplicationResult apply(RequestContextCommand command,
          CommandApplicationRequest request) {
        requestContextPriors.add(request.priorRequestContextExpansions());
        return CommandApplicationResult.CONTINUE;
      }
    };
    CommandApplier applier = new CommandApplier(List.of(capturingContinue,
        capturingRequestContext));
    ContextSelector selector = new ContextSelector(ContextSourceKind.STATE_KEY, "k",
        ContextVariant.FULL);
    ContextSelector otherSelector = new ContextSelector(ContextSourceKind.STATE_KEY, "k2",
        ContextVariant.FULL);

    applier.apply(
        List.of(
            new RequestContextCommand(List.of(selector)),
            new ContinueCommand(null, null, null),
            new RequestContextCommand(List.of(selector, otherSelector)),
            new ContinueCommand(null, null, null),
            new RequestContextCommand(List.of(selector))),
        stateAtStep("s1"), ContextMapping.none(), "agent-1", 1, step("s1"), workflow());

    assertThat(requestContextPriors).containsExactly(0, 1, 3);
    assertThat(continuePriors).containsExactly(0, 0);
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

  private static StepDefinition step(String stepId) {
    return StepDefinition.builder().withStepId(stepId).withName(stepId)
        .withBehaviour(new FailBehaviour("stop")).build();
  }

  private static WorkflowDefinition workflow() {
    return new WorkflowDefinition("wf-1", "W", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step("s1")),
        List.of(), List.of());
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
        new CreateFileCommandHandler(eventRecorder, resolvedFileSink,
            new InMemoryGeneratedArtifactStore()),
        new EscalateCommandHandler(eventRecorder, resolvedClock),
        new GeneralQuestionCommandHandler(eventRecorder, resolvedClock),
        new RunCommandHandler(eventRecorder, resolvedShell),
        new SetContextCommandHandler(eventRecorder),
        new UserPromptCommandHandler(eventRecorder, resolvedClock));
  }
}
