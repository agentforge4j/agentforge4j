// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.client;

import com.agentforge4j.util.Validate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of MCP server connections keyed by server id. It owns connection lifecycle only
 * (server-id to {@link McpServerConnection}); it never resolves a capability to a provider — that
 * is the job of the runtime's {@code ToolProviderResolver}.
 */
public final class McpServerRegistry {

  private final Map<String, McpServerConnection> connections = new ConcurrentHashMap<>();

  /**
   * Registers (or replaces) a connection under its server id.
   *
   * @param connection the connection to register
   */
  public void register(McpServerConnection connection) {
    Validate.notNull(connection, "connection must not be null");
    connections.put(connection.serverId(), connection);
  }

  /**
   * Looks up the connection registered under a server id.
   *
   * @param serverId non-blank server id
   *
   * @return the connection for {@code serverId}, or {@code null} if none is registered
   */
  public McpServerConnection connection(String serverId) {
    Validate.notBlank(serverId, "serverId must not be blank");
    return connections.get(serverId);
  }

  /**
   * @return all registered connections
   */
  public Collection<McpServerConnection> connections() {
    return List.copyOf(connections.values());
  }

  /**
   * Closes and removes every registered connection.
   */
  public void closeAll() {
    connections.values().forEach(McpServerConnection::close);
    connections.clear();
  }
}
