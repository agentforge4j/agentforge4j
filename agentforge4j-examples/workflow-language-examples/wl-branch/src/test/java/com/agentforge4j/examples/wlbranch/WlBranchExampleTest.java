// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlbranch;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

/**
 * Proves the Branch example routes deterministically: the {@code decide} agent's recorded decision
 * drives the {@code BRANCH} step, with {@code "approve"} reaching {@code COMPLETED} and
 * {@code "reject"} reaching {@code FAILED} via the {@code FAIL} branch. Public API plus the example's
 * own wiring only — no mocks, no network.
 */
class WlBranchExampleTest {

  @Test
  void approveBranchRoutesToCompletion() throws Exception {
    AgentForge4j agentForge4j = WlBranchExample.assemble(WlBranchExample.APPROVE);

    String runId = agentForge4j.start(WlBranchExample.WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(decision(state)).isEqualTo(WlBranchExample.APPROVE);
  }

  @Test
  void rejectBranchRoutesToFailure() throws Exception {
    AgentForge4j agentForge4j = WlBranchExample.assemble(WlBranchExample.REJECT);

    String runId = agentForge4j.start(WlBranchExample.WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(decision(state)).isEqualTo(WlBranchExample.REJECT);
  }

  private static String decision(WorkflowState state) {
    return ((StringContextValue) state.getContextValue(WlBranchExample.DECISION_KEY).orElseThrow())
        .value();
  }
}
