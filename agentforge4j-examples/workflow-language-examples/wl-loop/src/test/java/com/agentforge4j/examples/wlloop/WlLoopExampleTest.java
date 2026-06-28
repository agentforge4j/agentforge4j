// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlloop;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

/**
 * Proves both loop variants iterate and terminate deterministically: the {@code FIXED_COUNT} loop
 * runs exactly its configured iteration count, and the {@code AGENT_SIGNAL} loop runs until the agent
 * emits {@code COMPLETE}. Both reach {@code COMPLETED}. Iteration counts are tallied from
 * {@code LOOP_ITERATION_STARTED} events via the internal integrator accessor
 * {@code components().workflowEventLog()}. No mocks, no network.
 */
class WlLoopExampleTest {

  @Test
  void fixedCountLoopRunsExactlyMaxIterations() throws Exception {
    AgentForge4j agentForge4j = WlLoopExample.assemble();

    String runId = agentForge4j.start(WlLoopExample.FIXED_WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(WlLoopExample.loopIterations(agentForge4j, runId))
        .isEqualTo(WlLoopExample.FIXED_ITERATIONS);
  }

  @Test
  void agentSignalLoopRunsUntilTheAgentSignalsCompletion() throws Exception {
    AgentForge4j agentForge4j = WlLoopExample.assemble();

    String runId = agentForge4j.start(WlLoopExample.SIGNAL_WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(WlLoopExample.loopIterations(agentForge4j, runId))
        .isEqualTo(WlLoopExample.SIGNAL_ITERATIONS);
  }
}
