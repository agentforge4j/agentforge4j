// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlresource;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

/**
 * Proves the Resource example loads bundled content into the context with no LLM call: the run
 * completes and the configured context key holds the resource's text. The test uses the example's own
 * empty fake via {@link WlResourceFakeLlm}, so it stays offline — public API plus the example's own
 * wiring only, no mocks, no network.
 */
class WlResourceAppTest {

  @Test
  void resourceStepLoadsContentIntoContext() throws Exception {
    AgentForge4j agentForge4j = WlResourceApp.assemble();

    String runId = agentForge4j.start(WlResourceApp.WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(WlResourceApp.loadedResource(state))
        .isInstanceOfSatisfying(StringContextValue.class,
            value -> assertThat(value.value()).contains("loaded by a RESOURCE step"));
  }
}
