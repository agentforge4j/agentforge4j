// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.mcp;

import com.agentforge4j.mcp.client.transport.McpTransport;
import com.agentforge4j.mcp.client.transport.RemoteTool;
import com.agentforge4j.mcp.client.transport.RemoteToolResult;
import java.util.List;

/**
 * An in-process {@link McpTransport} that exposes one fixed tool and returns a fixed result. It stands in for a real
 * MCP server so the example runs deterministically and offline — no stdio subprocess (e.g. {@code npx}) and no
 * network.
 *
 * <p>A production transport is {@code StdioTransport} or {@code StreamableHttpTransport}; both talk
 * to a real server. This stub implements the same framework-owned {@link McpTransport} interface, so the rest of the
 * wiring ({@code McpServerConnection}, {@code McpToolProvider}) is identical to a real deployment.
 */
final class StubMcpTransport implements McpTransport {

  /**
   * The single tool this stub exposes; the agent invokes it by this capability name.
   */
  static final String TOOL_NAME = "echo";

  private boolean started;

  @Override
  public void start() {
    started = true;
  }

  @Override
  public List<RemoteTool> listTools() {
    return List.of(new RemoteTool(
        TOOL_NAME,
        "Echoes back a fixed acknowledgement.",
        "{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}}}"));
  }

  @Override
  public RemoteToolResult callTool(String remoteToolName, String argumentsJson) {
    return RemoteToolResult.success("{\"echoed\":\"hello from MCP\"}");
  }

  @Override
  public boolean isReady() {
    return started;
  }

  @Override
  public void close() {
    started = false;
  }
}
