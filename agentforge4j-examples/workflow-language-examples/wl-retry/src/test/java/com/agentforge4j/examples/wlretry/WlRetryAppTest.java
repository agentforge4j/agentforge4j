// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlretry;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves {@code RETRY_PREVIOUS} re-executes the previous step deterministically: after the first
 * input, the retry rewinds to the {@code INPUT} step, so the run re-suspends at
 * {@code AWAITING_INPUT}; after the second input the single attempt is exhausted and the run falls
 * through to the fallback agent and completes. The test always installs the deterministic fake via
 * {@link WlRetryFakeLlm}, independent of any environment configuration, so it stays offline — public
 * API plus the example's own wiring only, no mocks, no network.
 */
class WlRetryAppTest {

  @Test
  void retryRewindsToPreviousInputThenCompletesViaFallback() throws Exception {
    AgentForge4j agentForge4j = WlRetryApp.assembleWithFake(WlRetryFakeLlm.resolver());

    String runId = agentForge4j.start(WlRetryApp.WORKFLOW_ID);
    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_INPUT);

    // First input completes the request step; the retry then rewinds to it and re-requests input.
    agentForge4j.runtime().submitInput(runId,
        Map.of(WlRetryApp.FORM_FIELD, "first note"), "requester");
    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_INPUT);

    // Second input: the single retry attempt is now exhausted, so the run falls through to the
    // fallback agent step and completes.
    agentForge4j.runtime().submitInput(runId,
        Map.of(WlRetryApp.FORM_FIELD, "second note"), "requester");

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void assembleResolvesRealProviderClientWithoutNetworkCall() throws Exception {
    System.setProperty("agentforge4j.example.llm.provider", "openai");
    System.setProperty("agentforge4j.example.llm.api-key", "sk-test-not-a-real-key");
    try {
      ExampleLlmConfig config = ExampleLlmConfig.load();

      AgentForge4j agentForge4j = WlRetryApp.assemble(config);

      assertThat(agentForge4j).isNotNull();
    } finally {
      System.clearProperty("agentforge4j.example.llm.provider");
      System.clearProperty("agentforge4j.example.llm.api-key");
    }
  }
}
