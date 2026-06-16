// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.integration;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationToolProviderFactory;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.ToolProviderFactoryContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.mcp.client.transport.StdioTransport;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Realises {@link IntegrationType#MCP_STDIO} integrations: launches the configured MCP server
 * subprocess over stdio. The {@code config} payload carries a required {@code command} plus
 * optional {@code args} (array of strings), {@code env} (object of string values), and
 * {@code requestTimeout} (ISO-8601 duration; defaults to
 * {@link McpIntegrations#DEFAULT_REQUEST_TIMEOUT} when omitted). The resulting provider id is
 * {@code "mcp:" + definition.id()}.
 * <p>
 * Discovered via {@link java.util.ServiceLoader}; the connection is not started here — the provider
 * connects lazily on first use.
 */
public final class McpStdioToolProviderFactory implements IntegrationToolProviderFactory {

  @Override
  public IntegrationType supportedType() {
    return IntegrationType.MCP_STDIO;
  }

  @Override
  public ToolProvider create(IntegrationDefinition definition, ToolProviderFactoryContext context) {
    Validate.notNull(definition, "definition must not be null");
    Validate.notNull(context, "context must not be null");
    Validate.isTrue(definition.type() == IntegrationType.MCP_STDIO,
        "Integration '%s' has type %s; this factory only supports MCP_STDIO"
            .formatted(definition.id(), definition.type()));
    ObjectMapper mapper = context.objectMapper();
    JsonNode config = McpIntegrations.parseConfig(definition, mapper);
    String command = McpIntegrations.requiredText(config, "command", definition);
    StdioTransport transport = new StdioTransport(command, args(config), env(config),
        McpIntegrations.requestTimeout(config, definition), McpIntegrations.mcpJsonMapper(mapper));
    return McpIntegrations.toProvider(definition, transport);
  }

  private static List<String> args(JsonNode config) {
    JsonNode argsNode = config.get("args");
    if (argsNode == null || argsNode.isNull()) {
      return List.of();
    }
    List<String> args = new ArrayList<>();
    for (JsonNode arg : argsNode) {
      args.add(arg.asText());
    }
    return List.copyOf(args);
  }

  private static Map<String, String> env(JsonNode config) {
    JsonNode envNode = config.get("env");
    if (envNode == null || envNode.isNull()) {
      return Map.of();
    }
    Map<String, String> env = new LinkedHashMap<>();
    envNode.properties().iterator()
        .forEachRemaining(entry -> env.put(entry.getKey(), entry.getValue().asText()));
    return Map.copyOf(env);
  }
}
