// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.RunCommandCommand;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the {@link RunCommandHandler} storage contract: the runner's stdout lands under the
 * {@code <stepId>.stdout} step-output key (empty when the runner returns {@code null}, as the no-op
 * runner does), a {@code CONTEXT_UPDATED} event names the command, and a runner failure propagates
 * instead of being swallowed.
 */
class RunCommandHandlerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  private final InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
  private final EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);

  @Test
  void stores_runner_stdout_under_step_stdout_key_and_records_event() {
    RunCommandHandler handler = new RunCommandHandler(eventRecorder,
        (runId, command) -> "captured output for " + command);
    WorkflowState state = stateAtStep("s1");

    CommandApplicationResult result = handler.apply(new RunCommandCommand("do-thing"),
        new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1));

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    assertThat(state.getStepOutputs()).containsEntry("s1.stdout", "captured output for do-thing");
    List<WorkflowEvent> events = eventLog.getEvents("run-1");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventType()).isEqualTo(WorkflowEventType.CONTEXT_UPDATED);
    assertThat(events.get(0).payload()).contains("do-thing");
    assertThat(events.get(0).actorId()).isEqualTo("agent-1");
  }

  @Test
  void null_stdout_from_the_runner_is_stored_as_empty_not_null() {
    RunCommandHandler handler = new RunCommandHandler(eventRecorder, (runId, command) -> null);
    WorkflowState state = stateAtStep("s1");

    handler.apply(new RunCommandCommand("ignored"),
        new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1));

    assertThat(state.getStepOutputs()).containsEntry("s1.stdout", "");
  }

  @Test
  void no_op_runner_stores_empty_stdout() {
    RunCommandHandler handler = new RunCommandHandler(eventRecorder,
        ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER);
    WorkflowState state = stateAtStep("s1");

    handler.apply(new RunCommandCommand("ignored"),
        new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1));

    assertThat(state.getStepOutputs()).containsEntry("s1.stdout", "");
  }

  @Test
  void runner_failure_propagates_and_stores_no_stdout() {
    RunCommandHandler handler = new RunCommandHandler(eventRecorder,
        (runId, command) -> {
          throw new IllegalStateException("runner exploded");
        });
    WorkflowState state = stateAtStep("s1");

    assertThatThrownBy(() -> handler.apply(new RunCommandCommand("do-thing"),
        new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("runner exploded");
    assertThat(state.getStepOutputs()).doesNotContainKey("s1.stdout");
    assertThat(eventLog.getEvents("run-1")).isEmpty();
  }

  private static WorkflowState stateAtStep(String stepId) {
    WorkflowState state =
        new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    state.setCurrentStepId(stepId);
    return state;
  }
}
