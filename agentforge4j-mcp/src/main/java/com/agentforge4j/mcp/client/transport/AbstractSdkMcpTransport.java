// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.client.transport;

import com.agentforge4j.mcp.client.McpToolInvocationException;
import com.agentforge4j.util.Validate;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Base {@link McpTransport} backed by an MCP SDK {@link McpSyncClient}. Concrete strategies supply
 * the SDK transport via {@link #createSdkTransport()}; all SDK schema mapping lives here so it
 * stays in one place.
 */
abstract class AbstractSdkMcpTransport implements McpTransport {

  private static final System.Logger LOG = System.getLogger(
      AbstractSdkMcpTransport.class.getName());

  private final McpJsonMapper jsonMapper;
  private final Duration requestTimeout;
  private McpSyncClient client;

  AbstractSdkMcpTransport(McpJsonMapper jsonMapper, Duration requestTimeout) {
    this.jsonMapper = Validate.notNull(jsonMapper, "jsonMapper must not be null");
    this.requestTimeout = Validate.notNull(requestTimeout, "requestTimeout must not be null");
  }

  /**
   * @return the JSON mapper used by subclasses to build the SDK transport
   */
  protected final McpJsonMapper jsonMapper() {
    return jsonMapper;
  }

  /**
   * @return a freshly constructed SDK client transport for this strategy
   */
  protected abstract McpClientTransport createSdkTransport();

  @Override
  public final synchronized void start() {
    if (client != null) {
      return;
    }
    McpSyncClient created = McpClient.sync(createSdkTransport())
        .requestTimeout(requestTimeout)
        .build();
    created.initialize();
    client = created;
    LOG.log(System.Logger.Level.DEBUG, "MCP SDK transport initialized");
  }

  @Override
  public final synchronized List<RemoteTool> listTools() {
    McpSyncClient active = requireClient();
    McpSchema.ListToolsResult result = active.listTools();
    List<RemoteTool> tools = result.tools().stream().map(
            tool -> new RemoteTool(tool.name(), tool.description(), writeSchema(tool.inputSchema())))
        .toList();
    return List.copyOf(tools);
  }

  @Override
  public final synchronized RemoteToolResult callTool(String remoteToolName, String argumentsJson) {
    Validate.notBlank(remoteToolName, "remoteToolName must not be blank");
    McpSyncClient active = requireClient();
    String args = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
    McpSchema.CallToolResult result =
        active.callTool(new McpSchema.CallToolRequest(jsonMapper, remoteToolName, args));
    if (Boolean.TRUE.equals(result.isError())) {
      return RemoteToolResult.error(extractOutput(result));
    }
    return RemoteToolResult.success(extractOutput(result));
  }

  @Override
  public final synchronized boolean isReady() {
    return client != null && client.isInitialized();
  }

  @Override
  public final synchronized void close() {
    if (client == null) {
      return;
    }
    try {
      client.closeGracefully();
    } finally {
      client = null;
    }
  }

  private McpSyncClient requireClient() {
    return Validate.notNull(client,
        () -> new IllegalStateException("MCP transport has not been started"));
  }

  private String writeSchema(McpSchema.JsonSchema schema) {
    if (schema == null) {
      return null;
    }
    try {
      return jsonMapper.writeValueAsString(schema);
    } catch (IOException e) {
      throw new McpToolInvocationException("Failed to serialize tool input schema", e);
    }
  }

  private String extractOutput(McpSchema.CallToolResult result) {
    if (result.structuredContent() != null) {
      try {
        return jsonMapper.writeValueAsString(result.structuredContent());
      } catch (IOException e) {
        LOG.log(System.Logger.Level.DEBUG,
            "Falling back to text content; structured content not serializable");
      }
    }
    if (result.content() == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    for (McpSchema.Content content : result.content()) {
      if (content instanceof McpSchema.TextContent textContent) {
        builder.append(textContent.text());
      }
    }
    return builder.isEmpty() ? null : builder.toString();
  }
}
