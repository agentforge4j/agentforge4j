// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.support;

import com.agentforge4j.mcp.client.transport.McpTransport;
import com.agentforge4j.mcp.client.transport.RemoteTool;
import com.agentforge4j.mcp.client.transport.RemoteToolResult;
import java.util.List;

/**
 * In-process scripted {@link McpTransport} for MCP governance verification — no stdio subprocess and
 * no HTTP. It advertises a single {@code mcp.echo} tool and returns a canned success result for it,
 * so a workflow can drive a {@code McpToolProvider} through the runtime tool chokepoint without a
 * real MCP server. {@code McpTransport} is explicitly the seam tests supply a scripted implementation
 * for.
 */
public final class ScriptedMcpTransport implements McpTransport {

  /** The single remote tool this scripted server exposes. */
  public static final String TOOL_NAME = "mcp.echo";

  private volatile boolean ready;

  @Override
  public void start() {
    ready = true;
  }

  @Override
  public List<RemoteTool> listTools() {
    return List.of(new RemoteTool(TOOL_NAME, "Echoes its arguments", "{\"type\":\"object\"}"));
  }

  @Override
  public RemoteToolResult callTool(String remoteToolName, String argumentsJson) {
    if (!TOOL_NAME.equals(remoteToolName)) {
      return RemoteToolResult.error("unknown tool: " + remoteToolName);
    }
    return RemoteToolResult.success("{\"echoed\":true}");
  }

  @Override
  public boolean isReady() {
    return ready;
  }

  @Override
  public void close() {
    ready = false;
  }
}
