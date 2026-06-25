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
 * through to the fallback agent and completes. Public API plus the example's own wiring only — no
 * mocks, no network.
 */
class WlRetryExampleTest {

  @Test
  void retryRewindsToPreviousInputThenCompletesViaFallback() throws Exception {
    AgentForge4j agentForge4j = WlRetryExample.assemble();

    String runId = agentForge4j.start(WlRetryExample.WORKFLOW_ID);
    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_INPUT);

    // First input completes the request step; the retry then rewinds to it and re-requests input.
    agentForge4j.runtime().submitInput(runId,
        Map.of(WlRetryExample.FORM_FIELD, "first note"), "requester");
    assertThat(agentForge4j.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_INPUT);

    // Second input: the single retry attempt is now exhausted, so the run falls through to the
    // fallback agent step and completes.
    agentForge4j.runtime().submitInput(runId,
        Map.of(WlRetryExample.FORM_FIELD, "second note"), "requester");

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }
}
