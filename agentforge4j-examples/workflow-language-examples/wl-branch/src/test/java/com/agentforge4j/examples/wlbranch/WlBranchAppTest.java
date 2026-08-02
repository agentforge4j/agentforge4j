// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlbranch;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

/**
 * Proves the Branch example routes deterministically: the {@code decide} agent's recorded decision drives
 * the {@code BRANCH} step, with {@code "approve"} reaching {@code COMPLETED} and {@code "reject"} reaching
 * {@code FAILED} via the {@code FAIL} branch. The test always installs the deterministic fake via
 * {@link WlBranchFakeLlm}, independent of any environment configuration, so it stays offline — public API
 * plus the example's own wiring only, no mocks, no network.
 */
class WlBranchAppTest {

  @Test
  void approveBranchRoutesToCompletion() throws Exception {
    AgentForge4j agentForge4j = WlBranchApp.assembleWithFake(WlBranchFakeLlm.resolver(WlBranchApp.APPROVE));

    String runId = agentForge4j.start(WlBranchApp.WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(decision(state)).isEqualTo(WlBranchApp.APPROVE);
  }

  @Test
  void rejectBranchRoutesToFailure() throws Exception {
    AgentForge4j agentForge4j = WlBranchApp.assembleWithFake(WlBranchFakeLlm.resolver(WlBranchApp.REJECT));

    String runId = agentForge4j.start(WlBranchApp.WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(decision(state)).isEqualTo(WlBranchApp.REJECT);
  }

  @Test
  void assembleResolvesRealProviderClientWithoutNetworkCall() throws Exception {
    System.setProperty("agentforge4j.example.llm.provider", "openai");
    System.setProperty("agentforge4j.example.llm.api-key", "sk-test-not-a-real-key");
    try {
      ExampleLlmConfig config = ExampleLlmConfig.load();

      AgentForge4j agentForge4j = WlBranchApp.assemble(config);

      assertThat(agentForge4j).isNotNull();
    } finally {
      System.clearProperty("agentforge4j.example.llm.provider");
      System.clearProperty("agentforge4j.example.llm.api-key");
    }
  }

  private static String decision(WorkflowState state) {
    return ((StringContextValue) state.getContextValue(WlBranchApp.DECISION_KEY).orElseThrow())
        .value();
  }
}
