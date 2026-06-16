// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.client.transport;

import java.util.List;

/**
 * A session to a single MCP server, abstracted away from the MCP SDK so callers (and tests) depend
 * only on transport-neutral types. Concrete strategies are {@link StdioTransport} and
 * {@link StreamableHttpTransport}; tests supply a scripted implementation.
 */
public interface McpTransport {

  /**
   * Establishes the session (connect and initialize). Idempotent.
   */
  void start();

  /**
   * @return the tools currently exposed by the remote server
   */
  List<RemoteTool> listTools();

  /**
   * Invokes a remote tool.
   *
   * @param remoteToolName the tool's name on the remote server
   * @param argumentsJson  arguments as JSON text, or {@code null}
   *
   * @return the call result
   */
  RemoteToolResult callTool(String remoteToolName, String argumentsJson);

  /**
   * @return {@code true} when the session is established and ready to serve calls
   */
  boolean isReady();

  /**
   * Closes the session. Idempotent.
   */
  void close();
}
