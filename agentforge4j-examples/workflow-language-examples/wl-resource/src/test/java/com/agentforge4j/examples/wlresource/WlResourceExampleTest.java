// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlresource;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Resource example loads bundled content into the context with no LLM call: the run
 * completes and the configured context key holds the resource's text. Public API plus the example's
 * own wiring only — no mocks, no network.
 */
class WlResourceExampleTest {

  @Test
  void resourceStepLoadsContentIntoContext() throws Exception {
    AgentForge4j agentForge4j = WlResourceExample.assemble();

    String runId = agentForge4j.start(WlResourceExample.WORKFLOW_ID);

    WorkflowState state = agentForge4j.runtime().getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(WlResourceExample.loadedResource(state))
        .isInstanceOfSatisfying(StringContextValue.class,
            value -> assertThat(value.value()).contains("loaded by a RESOURCE step"));
  }

  @Test
  void displayValueRendersPlainContentNotRecordSyntax() throws Exception {
    AgentForge4j agentForge4j = WlResourceExample.assemble();
    String runId = agentForge4j.start(WlResourceExample.WORKFLOW_ID);
    WorkflowState state = agentForge4j.runtime().getState(runId);

    String rendered = WlResourceExample.displayValue(WlResourceExample.loadedResource(state));

    assertThat(rendered)
        .contains("loaded by a RESOURCE step")
        .doesNotContain("StringContextValue[")
        .doesNotContain("provenance=");
  }
}
