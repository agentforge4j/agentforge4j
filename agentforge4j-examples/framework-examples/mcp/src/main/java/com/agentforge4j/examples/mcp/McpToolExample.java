// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.mcp;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolSourceKind;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeResponse;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptKey;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import com.agentforge4j.mcp.client.McpServerConnection;
import com.agentforge4j.mcp.client.McpToolProvider;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An embedded workflow whose agent calls a tool exposed over the Model Context Protocol. The agent emits a single
 * {@code TOOL_INVOCATION} (which runs inline) followed by {@code COMPLETE}; the MCP tool's result is recorded in the
 * run context under {@code tool.<capability>}.
 *
 * <p>The run is deterministic and offline. The MCP server is an in-process {@link StubMcpTransport}
 * (no subprocess, no network), and the agent's one LLM call is scripted by the deterministic fake provider. The wiring
 * — {@link McpServerConnection} and {@link McpToolProvider} over the transport — is identical to a real MCP deployment;
 * only the transport is swapped.
 */
public final class McpToolExample {

  /**
   * Workflow id; matches {@code workflows/mcp-demo.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "mcp-demo";

  /**
   * The single step's id; matches the {@code stepId} in the workflow definition.
   */
  static final String STEP_ID = "call-tool";

  /**
   * Agent id; matches {@code agents/mcp-agent.agent/} and the step's {@code agentRef}.
   */
  static final String AGENT_ID = "mcp-agent";

  /**
   * The MCP server / provider id.
   */
  static final String SERVER_ID = "mcp:demo";

  /**
   * The tool capability the agent invokes — an MCP tool's capability is its remote name.
   */
  static final String CAPABILITY = StubMcpTransport.TOOL_NAME;

  /**
   * Context key the tool result is stored under after the call.
   */
  static final String TOOL_CONTEXT_KEY = "tool." + CAPABILITY;

  /**
   * The scripted model output for the agent's single call: a bare JSON array that invokes the MCP tool and then
   * completes. The tool runs inline between the two commands; the agent is not re-invoked.
   */
  static final String SCRIPTED_TOOL_THEN_COMPLETE =
      "[{\"type\":\"TOOL_INVOCATION\",\"capability\":\"echo\","
          + "\"arguments\":{\"message\":\"hello from MCP\"}},{\"type\":\"COMPLETE\"}]";

  private McpToolExample() {
  }

  /**
   * Runs the workflow and prints the terminal status and the MCP tool result.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    AgentForge4j agentForge4j = assemble();

    String runId = agentForge4j.start(WORKFLOW_ID);
    WorkflowState state = agentForge4j.runtime().getState(runId);

    System.out.printf("Workflow '%s' (run %s) finished with status: %s%n", WORKFLOW_ID, runId, state.getStatus());
    // On the documented deterministic path the result is always a StringContextValue; if the run
    // failed (or the key is absent), print what is there instead of crashing on the unwrap.
    ContextValue toolResult = state.getContext().get(TOOL_CONTEXT_KEY);
    String toolResultText = toolResult instanceof StringContextValue stringResult
        ? stringResult.value()
        : String.valueOf(toolResult);
    System.out.printf("Tool result (%s): %s%n", TOOL_CONTEXT_KEY, toolResultText);
  }

  /**
   * Assembles an AgentForge4j runtime wired to the deterministic fake provider, this module's own config, and an MCP
   * tool provider backed by the in-process stub transport.
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble() throws URISyntaxException {
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(WORKFLOW_ID, STEP_ID, AGENT_ID, 0),
        new FakeResponse(SCRIPTED_TOOL_THEN_COMPLETE, null)));
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));

    // REMOTE_HTTP is the structural kind for an MCP server reached over the network (streamable
    // HTTP); the in-process stub stands in for such a server, so it carries the same kind.
    ToolProvider mcpToolProvider = new McpToolProvider(
        SERVER_ID, new McpServerConnection(SERVER_ID, new StubMcpTransport()),
        ToolSourceKind.REMOTE_HTTP);

    return AgentForge4jBootstrap.defaults()
        .withWorkflowsDir(resourceDirectory("/workflows"))
        .withAgentsDir(resourceDirectory("/agents"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .withLlmClientResolver(new DefaultLlmClientResolver(List.of(fakeLlmClient)))
        .withToolProviders(List.of(mcpToolProvider))
        // The secure default policy denies REMOTE_HTTP tools (the MCP source kind here); this
        // self-contained demo trusts its own in-process stub server, so it opts in with allowAll().
        // Production code must use a policy that reflects its actual trust boundary.
        .withToolPolicy(ToolPolicy.allowAll())
        .build();
  }

  private static Path resourceDirectory(String classpathDirectory) throws URISyntaxException {
    return Path.of(Objects.requireNonNull(
        McpToolExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
