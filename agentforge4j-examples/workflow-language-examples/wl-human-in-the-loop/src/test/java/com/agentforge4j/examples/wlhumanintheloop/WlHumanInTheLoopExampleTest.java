// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlhumanintheloop;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the human-in-the-loop example suspends and resumes deterministically: the run pauses at
 * {@code AWAITING_INPUT}, then at {@code AWAITING_STEP_APPROVAL}; an approve reaches {@code COMPLETED}
 * and a reject fails it with a {@code StepRejectionFailure}. Public API plus the example's own wiring
 * only — no mocks, no network.
 */
class WlHumanInTheLoopExampleTest {

  private static final String SUMMARY = "Ship the release";

  @Test
  void approvePathReachesCompleted() throws Exception {
    AgentForge4j agentForge4j = WlHumanInTheLoopExample.assemble();

    String runId = agentForge4j.start(WlHumanInTheLoopExample.WORKFLOW_ID);
    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_INPUT);

    agentForge4j.runtime().submitInput(runId,
        Map.of(WlHumanInTheLoopExample.FORM_FIELD, SUMMARY), "requester");
    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);

    agentForge4j.runtime().decideStepApproval(runId, WlHumanInTheLoopExample.REVIEW_STEP_ID,
        new StepApprovalDecision.Approve("alice", "Looks good"));

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getContextValue(
        WlHumanInTheLoopExample.ARTIFACT_ID + "." + WlHumanInTheLoopExample.FORM_FIELD))
        .get()
        .isInstanceOfSatisfying(StringContextValue.class,
            value -> assertThat(value.value()).isEqualTo(SUMMARY));

    assertThat(eventTypes(agentForge4j, runId))
        .contains(WorkflowEventType.AWAITING_INPUT,
            WorkflowEventType.STEP_AWAITING_APPROVAL,
            WorkflowEventType.STEP_APPROVED)
        .doesNotContain(WorkflowEventType.STEP_REJECTED);
  }

  @Test
  void rejectPathFailsWithStepRejectionFailure() throws Exception {
    AgentForge4j agentForge4j = WlHumanInTheLoopExample.assemble();

    String runId = agentForge4j.start(WlHumanInTheLoopExample.WORKFLOW_ID);
    agentForge4j.runtime().submitInput(runId,
        Map.of(WlHumanInTheLoopExample.FORM_FIELD, SUMMARY), "requester");
    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);

    agentForge4j.runtime().decideStepApproval(runId, WlHumanInTheLoopExample.REVIEW_STEP_ID,
        new StepApprovalDecision.Reject("alice", "Needs rework"));

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(state.getRunFailure()).isInstanceOf(RunFailure.StepRejectionFailure.class);
    assertThat(state.getFailureReason()).isEqualTo("Needs rework");

    assertThat(eventTypes(agentForge4j, runId))
        .contains(WorkflowEventType.AWAITING_INPUT,
            WorkflowEventType.STEP_AWAITING_APPROVAL,
            WorkflowEventType.STEP_REJECTED)
        .doesNotContain(WorkflowEventType.STEP_APPROVED);
  }

  private static List<WorkflowEventType> eventTypes(AgentForge4j agentForge4j, String runId) {
    return agentForge4j.components().workflowEventLog().getEvents(runId).stream()
        .map(WorkflowEvent::eventType)
        .toList();
  }
}
