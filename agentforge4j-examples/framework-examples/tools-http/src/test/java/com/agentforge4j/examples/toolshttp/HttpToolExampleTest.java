// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.toolshttp;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the HTTP tool example is deterministic: against the example's own loopback server, the agent's scripted
 * {@code TOOL_INVOCATION} runs the tool and the run reaches {@code COMPLETED} with the tool result recorded in context.
 * Public API plus the example's own wiring only — no mocks, no real network.
 */
class HttpToolExampleTest {

  @Test
  void runInvokesHttpToolAndReachesCompleted() throws Exception {
    HttpServer stubServer = HttpToolExample.startStubServer();
    try {
      AgentForge4j agentForge4j = HttpToolExample.assemble(stubServer.getAddress().getPort());

      String runId = agentForge4j.start(HttpToolExample.WORKFLOW_ID);
      WorkflowState state = agentForge4j.runtime().getState(runId);

      assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
      assertThat(state.getContext()).containsKey(HttpToolExample.TOOL_CONTEXT_KEY);
      assertThat(state.getContext().get(HttpToolExample.TOOL_CONTEXT_KEY).toString())
          .contains("London", "Sunny", "18");
    } finally {
      stubServer.stop(0);
    }
  }
}
