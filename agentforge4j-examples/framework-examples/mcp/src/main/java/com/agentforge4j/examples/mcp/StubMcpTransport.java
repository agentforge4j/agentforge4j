// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.mcp;

import com.agentforge4j.mcp.client.transport.McpTransport;
import com.agentforge4j.mcp.client.transport.RemoteTool;
import com.agentforge4j.mcp.client.transport.RemoteToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

  private static final ObjectMapper MAPPER = new ObjectMapper();

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

  /**
   * Validates the call shape, then echoes the caller's {@code message} back. Rejecting an unknown
   * tool name and a missing {@code message} proves the round-trip carries the actual arguments rather
   * than answering blindly.
   *
   * @param remoteToolName the tool's name on the remote server; must equal {@link #TOOL_NAME}
   * @param argumentsJson  arguments as JSON text carrying a {@code message} string
   * @return a success result {@code {"echoed":"<message>"}}, or an error result for an unknown tool
   *     name, absent arguments, or a missing/non-string {@code message}
   */
  @Override
  public RemoteToolResult callTool(String remoteToolName, String argumentsJson) {
    if (!TOOL_NAME.equals(remoteToolName)) {
      return RemoteToolResult.error("Unknown tool: %s".formatted(remoteToolName));
    }
    if (argumentsJson == null) {
      return RemoteToolResult.error("Missing arguments for tool '%s'".formatted(TOOL_NAME));
    }
    try {
      JsonNode message = MAPPER.readTree(argumentsJson).get("message");
      if (message == null || !message.isTextual()) {
        return RemoteToolResult.error("Missing 'message' argument for tool '%s'".formatted(TOOL_NAME));
      }
      ObjectNode output = MAPPER.createObjectNode();
      output.put("echoed", message.asText());
      return RemoteToolResult.success(MAPPER.writeValueAsString(output));
    } catch (JsonProcessingException e) {
      return RemoteToolResult.error("Malformed arguments JSON for tool '%s'".formatted(TOOL_NAME));
    }
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
