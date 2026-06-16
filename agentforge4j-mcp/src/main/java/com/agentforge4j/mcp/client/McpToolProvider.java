// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.client;

import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.mcp.client.transport.RemoteTool;
import com.agentforge4j.mcp.client.transport.RemoteToolResult;
import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ToolProvider} backed by a single MCP server connection. Maps the server's remote tools to
 * logical {@link ToolDescriptor}s (capability defaulting to the remote tool name) and delegates
 * invocation to the connection. It resolves nothing across servers: capability resolution is the
 * resolver's job.
 *
 * <p>The realised tool set carries no trustworthy mutation hint (the transport surfaces only name,
 * description, and input schema), so every descriptor is tagged with
 * {@link ToolRiskMetadata#conservative()} — the highest safe risk.
 */
public final class McpToolProvider implements ToolProvider {

  private final String providerId;
  private final McpServerConnection connection;

  /**
   * Creates a provider over a single MCP server connection.
   *
   * @param providerId non-blank provider id, for example {@code "mcp:github-official"}
   * @param connection the connection to the backing MCP server
   */
  public McpToolProvider(String providerId, McpServerConnection connection) {
    this.providerId = Validate.notBlank(providerId, "providerId must not be blank");
    this.connection = Validate.notNull(connection, "connection must not be null");
  }

  @Override
  public String providerId() {
    return providerId;
  }

  @Override
  public List<ToolDescriptor> listTools() {
    List<ToolDescriptor> descriptors = new ArrayList<>();
    for (RemoteTool tool : connection.listTools()) {
      descriptors.add(new ToolDescriptor(
          tool.name(),
          tool.name(),
          tool.description(),
          tool.inputSchemaJson(),
          null,
          new ToolSource(providerId, tool.name()),
          ToolRiskMetadata.conservative()));
    }
    return List.copyOf(descriptors);
  }

  @Override
  public ToolResult invoke(ToolDescriptor descriptor, String arguments, ToolInvocationContext ctx,
      ToolExecutionOptions options) {
    Validate.notNull(descriptor, "descriptor must not be null");
    Validate.notNull(descriptor.source(), "descriptor source must not be null");
    long startNanos = System.nanoTime();
    RemoteToolResult result = connection.callTool(descriptor.source().remoteToolName(), arguments);
    long latencyMillis = (System.nanoTime() - startNanos) / 1_000_000L;
    return result.error()
        ? ToolResult.failure(result.errorMessage(), latencyMillis)
        : ToolResult.success(result.output(), latencyMillis);
  }

  @Override
  public HealthStatus health() {
    return connection.health();
  }
}
