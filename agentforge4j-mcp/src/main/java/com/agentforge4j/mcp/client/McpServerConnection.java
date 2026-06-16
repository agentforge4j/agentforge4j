// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.client;

import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.mcp.client.transport.McpTransport;
import com.agentforge4j.mcp.client.transport.RemoteTool;
import com.agentforge4j.mcp.client.transport.RemoteToolResult;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle wrapper around a single MCP server's {@link McpTransport}: it owns connect, reconnect,
 * health, and dispose for one server id. It is a connection manager, not a capability resolver —
 * capability resolution lives entirely in the runtime's {@code ToolProviderResolver}.
 */
public final class McpServerConnection {

  private static final System.Logger LOG = System.getLogger(McpServerConnection.class.getName());

  private final String serverId;
  private final McpTransport transport;
  private final AtomicReference<List<RemoteTool>> cachedTools = new AtomicReference<>();
  private volatile boolean started;

  /**
   * Selects the supplied transport strategy for {@code serverId}.
   *
   * @param serverId  non-blank server id
   * @param transport the chosen transport strategy
   */
  public McpServerConnection(String serverId, McpTransport transport) {
    this.serverId = Validate.notBlank(serverId, "serverId must not be blank");
    this.transport = Validate.notNull(transport, "transport must not be null");
  }

  /**
   * @return the server id this connection manages
   */
  public String serverId() {
    return serverId;
  }

  /**
   * Establishes the session if it is not already started.
   */
  public synchronized void start() {
    if (started) {
      return;
    }
    transport.start();
    started = true;
    LOG.log(System.Logger.Level.INFO, "MCP server connection started: {0}", serverId);
  }

  /**
   * Closes and re-establishes the session.
   */
  public synchronized void reconnect() {
    LOG.log(System.Logger.Level.INFO, "Reconnecting MCP server: {0}", serverId);
    closeQuietly();
    start();
  }

  /**
   * @return tools exposed by the remote server, starting the session on demand. The list is cached
   * per session and invalidated on {@link #reconnect()} / {@link #close()} so repeated resolver and
   * prompt-assembly lookups do not re-hit the remote server.
   */
  public List<RemoteTool> listTools() {
    ensureStarted();
    List<RemoteTool> cached = cachedTools.get();
    if (cached != null) {
      return cached;
    }
    List<RemoteTool> fetched = List.copyOf(transport.listTools());
    cachedTools.set(fetched);
    return fetched;
  }

  /**
   * Invokes a remote tool, starting the session on demand.
   *
   * @param remoteToolName the tool's name on the remote server
   * @param argumentsJson  arguments as JSON text, or {@code null}
   *
   * @return the call result
   */
  public RemoteToolResult callTool(String remoteToolName, String argumentsJson) {
    ensureStarted();
    return transport.callTool(remoteToolName, argumentsJson);
  }

  /**
   * @return current health of the connection
   */
  public HealthStatus health() {
    if (!started) {
      return new HealthStatus(HealthStatus.State.DOWN, "not started");
    }
    return transport.isReady()
        ? new HealthStatus(HealthStatus.State.UP, null)
        : new HealthStatus(HealthStatus.State.DEGRADED, "transport not ready");
  }

  /**
   * Closes the session.
   */
  public synchronized void close() {
    closeQuietly();
  }

  private void ensureStarted() {
    if (!started) {
      start();
    }
  }

  private void closeQuietly() {
    try {
      transport.close();
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.WARNING, "Error closing MCP transport for " + serverId, e);
    } finally {
      started = false;
      cachedTools.set(null);
    }
  }
}
