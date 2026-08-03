// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.mcp;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the MCP example is deterministic: against the in-process stub transport, the agent's scripted
 * {@code TOOL_INVOCATION} calls the MCP tool and the run reaches {@code COMPLETED} with the tool result recorded in
 * context. Public API plus the example's own wiring only — no mocks, no network, no subprocess.
 */
class McpToolExampleTest {

  @Test
  void runInvokesMcpToolAndReachesCompleted() throws Exception {
    AgentForge4j agentForge4j = McpToolExample.assemble();

    String runId = agentForge4j.start(McpToolExample.WORKFLOW_ID);
    WorkflowState state = agentForge4j.runtime().getState(runId);

    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getContext()).containsKey(McpToolExample.TOOL_CONTEXT_KEY);
    StringContextValue toolResult = (StringContextValue) state.getContext().get(McpToolExample.TOOL_CONTEXT_KEY);
    assertThat(toolResult.value()).isEqualTo("{\"echoed\":\"hello from MCP\"}");
  }
}
