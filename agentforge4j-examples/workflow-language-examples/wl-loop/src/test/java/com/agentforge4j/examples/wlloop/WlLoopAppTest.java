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
 * {@code components().workflowEventLog()}. The test always installs the deterministic fake via
 * {@link WlLoopFakeLlm}, independent of any environment configuration, so it stays offline — no mocks,
 * no network.
 */
class WlLoopAppTest {

  @Test
  void fixedCountLoopRunsExactlyMaxIterations() throws Exception {
    AgentForge4j agentForge4j = WlLoopApp.assembleWithFake(WlLoopFakeLlm.resolver());

    String runId = agentForge4j.start(WlLoopApp.FIXED_WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(WlLoopApp.loopIterations(agentForge4j, runId))
        .isEqualTo(WlLoopApp.FIXED_ITERATIONS);
  }

  @Test
  void agentSignalLoopRunsUntilTheAgentSignalsCompletion() throws Exception {
    AgentForge4j agentForge4j = WlLoopApp.assembleWithFake(WlLoopFakeLlm.resolver());

    String runId = agentForge4j.start(WlLoopApp.SIGNAL_WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(WlLoopApp.loopIterations(agentForge4j, runId))
        .isEqualTo(WlLoopApp.SIGNAL_ITERATIONS);
  }

  @Test
  void assembleResolvesRealProviderClientWithoutNetworkCall() throws Exception {
    System.setProperty("agentforge4j.example.llm.provider", "openai");
    System.setProperty("agentforge4j.example.llm.api-key", "sk-test-not-a-real-key");
    try {
      ExampleLlmConfig config = ExampleLlmConfig.load();

      AgentForge4j agentForge4j = WlLoopApp.assemble(config);

      assertThat(agentForge4j).isNotNull();
    } finally {
      System.clearProperty("agentforge4j.example.llm.provider");
      System.clearProperty("agentforge4j.example.llm.api-key");
    }
  }
}
