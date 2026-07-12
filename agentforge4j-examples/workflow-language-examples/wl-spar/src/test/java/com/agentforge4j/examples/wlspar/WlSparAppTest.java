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
 * completes the run. The test always installs the deterministic fake via {@link WlSparFakeLlm},
 * independent of any environment configuration, so it stays offline — public API plus the example's
 * own wiring only, no mocks, no network.
 */
class WlSparAppTest {

  @Test
  void sparRunsBothRoundsThenResolves() throws Exception {
    AgentForge4j agentForge4j = WlSparApp.assembleWithFake(WlSparFakeLlm.resolver());

    String runId = agentForge4j.start(WlSparApp.WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

    for (int round = 1; round <= WlSparApp.MAX_ROUNDS; round++) {
      assertThat(state.getContextValue(WlSparApp.PRIMARY_ROUND_PREFIX + round))
          .as("primary contribution for round %d", round)
          .isPresent();
      assertThat(state.getContextValue(WlSparApp.CHALLENGER_ROUND_PREFIX + round))
          .as("challenger contribution for round %d", round)
          .isPresent();
    }
  }

  @Test
  void assembleResolvesRealProviderClientWithoutNetworkCall() throws Exception {
    System.setProperty("agentforge4j.example.llm.provider", "openai");
    System.setProperty("agentforge4j.example.llm.api-key", "sk-test-not-a-real-key");
    try {
      ExampleLlmConfig config = ExampleLlmConfig.load();

      AgentForge4j agentForge4j = WlSparApp.assemble(config);

      assertThat(agentForge4j).isNotNull();
    } finally {
      System.clearProperty("agentforge4j.example.llm.provider");
      System.clearProperty("agentforge4j.example.llm.api-key");
    }
  }
}
