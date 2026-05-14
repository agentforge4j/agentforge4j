package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.command.UserPromptCommand;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPromptPauseGuardTest {

  @Test
  void throwsRuntimeUserPromptLimitExceptionWhenPauseBudgetIsExhausted() {
    StepDefinition step = new StepDefinition(
        "s1",
        "Step 1",
        new AgentBehaviour("agent-1", StepTransition.AUTO, null),
        ContextMapping.none(),
        null,
        2);
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    state.incrementUserPromptPauseCountForStep("s1");
    state.incrementUserPromptPauseCountForStep("s1");
    EventRecorder eventRecorder = new EventRecorder(
        new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC));
    List<LlmCommand> commands = List.of(new UserPromptCommand("Need your input", true));

    assertThatThrownBy(() -> UserPromptPauseGuard.ensureBlockingUserPromptAllowed(
        eventRecorder, step, state, commands))
        .isInstanceOf(UserPromptLimitExceededException.class)
        .hasMessageContaining("exceeded maxUserPromptRounds");
  }
}
