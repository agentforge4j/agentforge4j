// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.tool;

import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolSourceKind;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.mcp.client.McpServerConnection;
import com.agentforge4j.mcp.client.McpToolProvider;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.verification.support.Fixtures;
import com.agentforge4j.verification.support.ScriptedMcpTransport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves an {@link McpToolProvider} resolves and invokes through the runtime tool-governance
 * chokepoint, using a fully in-process {@link ScriptedMcpTransport} (no stdio subprocess, no HTTP).
 * An agent emits a {@code TOOL_INVOCATION} for the MCP-exposed {@code mcp.echo} capability; the
 * chokepoint resolves it against the MCP provider, invokes it through the connection/transport, and
 * the scripted success result completes the run.
 */
class McpToolGovernanceTest {

  private static FakeScript script() {
    try {
      return new FakeScriptParser().parse(
          Files.readString(Fixtures.dir("/fixtures/mcp/script.json")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read MCP fake script", e);
    }
  }

  @Test
  void mcpToolResolvesAndInvokesThroughTheGovernanceChokepoint() {
    ToolProvider provider = new McpToolProvider(
        "mcp:test", new McpServerConnection("test-server", new ScriptedMcpTransport()),
        ToolSourceKind.REMOTE_HTTP);

    WorkflowRunResult result = WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/tool/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/tool/agents"))
        .script(script())
        .toolProviders(List.of(provider))
        // MCP tools are remote, so the secure default policy denies them; this test verifies the
        // governance-chokepoint mechanics (resolve → invoke), not the deny, so opt in explicitly.
        .toolPolicy(ToolPolicy.allowAll())
        .build()
        .run("tool-run");

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .invokedTool(ScriptedMcpTransport.TOOL_NAME);
  }
}
