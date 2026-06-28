// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlspar;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

/**
 * Proves the SPAR example runs a genuine multi-round exchange and resolves deterministically: both
 * the primary and the challenger contribute in each of the two rounds (recorded under
 * {@code spar.primary.round.N} / {@code spar.challenger.round.N}), then the primary's resolution
 * completes the run. Public API plus the example's own wiring only — no mocks, no network.
 */
class WlSparExampleTest {

  @Test
  void sparRunsBothRoundsThenResolves() throws Exception {
    AgentForge4j agentForge4j = WlSparExample.assemble();

    String runId = agentForge4j.start(WlSparExample.WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

    for (int round = 1; round <= WlSparExample.MAX_ROUNDS; round++) {
      assertThat(state.getContextValue(WlSparExample.PRIMARY_ROUND_PREFIX + round))
          .as("primary contribution for round %d", round)
          .isPresent();
      assertThat(state.getContextValue(WlSparExample.CHALLENGER_ROUND_PREFIX + round))
          .as("challenger contribution for round %d", round)
          .isPresent();
    }
  }
}
