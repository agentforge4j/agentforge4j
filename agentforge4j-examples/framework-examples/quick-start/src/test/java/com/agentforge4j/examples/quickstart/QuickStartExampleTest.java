// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.quickstart;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Quick Start example is deterministic: with the fake provider scripting a single
 * {@code COMPLETE}, the workflow always reaches {@code COMPLETED}. Uses public API plus the
 * example's own wiring only — no mocks, no network.
 */
class QuickStartExampleTest {

  @Test
  void runReachesCompletedDeterministically() throws Exception {
    AgentForge4j agentForge4j = QuickStartExample.assemble();

    String runId = agentForge4j.start(QuickStartExample.WORKFLOW_ID);
    WorkflowState state = agentForge4j.runtime().getState(runId);

    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }
}
