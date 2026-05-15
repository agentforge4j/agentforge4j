package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.EscalateCommand;
import com.agentforge4j.core.command.GenerateQuestionsCommand;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.UserPromptCommand;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.exception.UserPromptLimitExceededException;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPromptPauseGuardTest {

  private static final StepDefinition STEP = new StepDefinition(
      "s1",
      "Step 1",
      new AgentBehaviour("agent-1", StepTransition.AUTO, null),
      ContextMapping.none(),
      null,
      8);

  @Test
  void throwsRuntimeUserPromptLimitExceptionWhenPauseBudgetIsExhausted() {
    StepDefinition limitedStep = new StepDefinition(
        "s1",
        "Step 1",
        new AgentBehaviour("agent-1", StepTransition.AUTO, null),
        ContextMapping.none(),
        null,
        2);
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    state.incrementUserPromptPauseCountForStep("s1");
    state.incrementUserPromptPauseCountForStep("s1");
    List<LlmCommand> commands = List.of(new UserPromptCommand("Need your input", true));

    assertThatThrownBy(() -> UserPromptPauseGuard.ensureBlockingUserPromptAllowed(
        eventRecorder(), limitedStep, state, commands))
        .isInstanceOf(UserPromptLimitExceededException.class)
        .hasMessageContaining("exceeded maxUserPromptRounds");
  }

  @Test
  void blocking_prompt_before_complete_returns_true() {
    WorkflowState state = freshState();
    List<LlmCommand> commands = List.of(
        new UserPromptCommand("pause", true),
        new CompleteCommand(null));

    assertThatCode(() -> UserPromptPauseGuard.ensureBlockingUserPromptAllowed(
        eventRecorder(), STEP, state, commands))
        .doesNotThrowAnyException();
  }

  @Test
  void blocking_prompt_after_complete_throws_protocol_violation() {
    List<LlmCommand> commands = List.of(
        new CompleteCommand(null),
        new UserPromptCommand("too late", true));

    assertThatThrownBy(() -> UserPromptPauseGuard.ensureBlockingUserPromptAllowed(
        eventRecorder(), STEP, freshState(), commands))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("protocol violation")
        .hasMessageContaining("COMPLETE")
        .hasMessageContaining("index 1")
        .hasMessageContaining("stepId=s1");
  }

  @Test
  void blocking_prompt_after_escalate_throws() {
    List<LlmCommand> commands = List.of(
        new EscalateCommand("reason"),
        new UserPromptCommand("too late", true));

    assertThatThrownBy(() -> UserPromptPauseGuard.ensureBlockingUserPromptAllowed(
        eventRecorder(), STEP, freshState(), commands))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("protocol violation")
        .hasMessageContaining("ESCALATE")
        .hasMessageContaining("index 1");
  }

  @Test
  void blocking_prompt_after_generate_questions_throws() {
    List<LlmCommand> commands = List.of(
        new GenerateQuestionsCommand(List.of(new TextArtifactItem("q1", "Question", true, null))),
        new UserPromptCommand("too late", true));

    assertThatThrownBy(() -> UserPromptPauseGuard.ensureBlockingUserPromptAllowed(
        eventRecorder(), STEP, freshState(), commands))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("protocol violation")
        .hasMessageContaining("GENERATE_QUESTIONS")
        .hasMessageContaining("index 1");
  }

  @Test
  void complete_alone_returns_false_without_throwing() {
    List<LlmCommand> commands = List.of(new CompleteCommand(null));

    assertThatCode(() -> UserPromptPauseGuard.ensureBlockingUserPromptAllowed(
        eventRecorder(), STEP, freshState(), commands))
        .doesNotThrowAnyException();
  }

  @Test
  void non_blocking_prompt_after_complete_does_not_throw() {
    List<LlmCommand> commands = List.of(
        new CompleteCommand(null),
        new UserPromptCommand("optional", false));

    assertThatCode(() -> UserPromptPauseGuard.ensureBlockingUserPromptAllowed(
        eventRecorder(), STEP, freshState(), commands))
        .doesNotThrowAnyException();
  }

  private static WorkflowState freshState() {
    return new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
  }

  private static EventRecorder eventRecorder() {
    return new EventRecorder(
        new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC));
  }
}
