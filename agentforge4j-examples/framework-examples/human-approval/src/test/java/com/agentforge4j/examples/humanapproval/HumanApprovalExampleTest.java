// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.humanapproval;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Human Approval example is deterministic: the run always suspends at {@code AWAITING_STEP_APPROVAL}; an
 * approve advances it to {@code COMPLETED}, and a reject fails it with a {@code StepRejectionFailure} carrying the
 * rejection reason. Public API plus the example's own wiring only — no mocks, no network.
 */
class HumanApprovalExampleTest {

  @Test
  void approveAdvancesToCompleted() throws Exception {
    AgentForge4j agentForge4j = HumanApprovalExample.assemble();

    String runId = agentForge4j.start(HumanApprovalExample.WORKFLOW_ID);
    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);

    agentForge4j.runtime().decideStepApproval(runId, HumanApprovalExample.STEP_ID,
        new StepApprovalDecision.Approve("alice", "Looks good"));

    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void rejectFailsRunWithStepRejectionFailure() throws Exception {
    AgentForge4j agentForge4j = HumanApprovalExample.assemble();

    String runId = agentForge4j.start(HumanApprovalExample.WORKFLOW_ID);
    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);

    agentForge4j.runtime().decideStepApproval(runId, HumanApprovalExample.STEP_ID,
        new StepApprovalDecision.Reject("alice", "Needs rework"));

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(state.getRunFailure()).isInstanceOf(RunFailure.StepRejectionFailure.class);
    assertThat(state.getFailureReason()).isEqualTo("Needs rework");
  }
}
