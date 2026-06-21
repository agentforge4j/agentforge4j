// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.integration;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationToolProviderFactory;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.ToolProviderFactoryContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolSourceKind;
import com.agentforge4j.mcp.client.transport.StreamableHttpTransport;
import com.agentforge4j.util.Validate;
import com.agentforge4j.util.net.EgressCheckResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;

/**
 * Realises {@link IntegrationType#MCP_STREAMABLE_HTTP} integrations: connects to a remote MCP server over Streamable
 * HTTP. The {@code config} payload carries a required {@code url} plus an optional {@code requestTimeout} (ISO-8601
 * duration; defaults to {@link McpIntegrations#DEFAULT_REQUEST_TIMEOUT} when omitted). The resulting provider id is
 * {@code "mcp:" + definition.id()}.
 * <p>
 * Discovered via {@link java.util.ServiceLoader}; the connection is not started here — the provider connects lazily on
 * first use.
 */
public final class McpStreamableHttpToolProviderFactory implements IntegrationToolProviderFactory {

  @Override
  public IntegrationType supportedType() {
    return IntegrationType.MCP_STREAMABLE_HTTP;
  }

  @Override
  public ToolProvider create(IntegrationDefinition definition, ToolProviderFactoryContext context) {
    Validate.notNull(definition, "definition must not be null");
    Validate.notNull(context, "context must not be null");
    Validate.isTrue(definition.type() == IntegrationType.MCP_STREAMABLE_HTTP,
        "Integration '%s' has type %s; this factory only supports MCP_STREAMABLE_HTTP"
            .formatted(definition.id(), definition.type()));
    ObjectMapper mapper = context.objectMapper();
    JsonNode config = McpIntegrations.parseConfig(definition, mapper);
    String url = McpIntegrations.requiredText(config, "url", definition);
    // Classify the configured server URL against the egress guard before any connection is built.
    // The MCP SDK owns the transport socket (it re-resolves DNS at connect), so this is a
    // pre-connect check with the same documented resolve-and-recheck residual TOCTOU as the HTTP
    // tool path — see HttpEgressGuard. The guard only classifies; refusing the configuration on a
    // denied target is this factory's reaction.
    EgressCheckResult egress = context.egressGuard().check(URI.create(url));
    Validate.isTrue(egress.allowed(), () -> new IllegalStateException(
        "MCP server '%s' streamable-HTTP url is not an allowed egress target: %s"
            .formatted(definition.id(), egress.reason())));
    // Integration-configured streamable-HTTP MCP servers carry no per-server headers yet (the
    // config payload has only 'url'); pass empty header maps and a null resolver. Header/secret
    // support is gated on the OSS SecretResolver SPI landing with agentforge4j-tools-http.
    StreamableHttpTransport transport = new StreamableHttpTransport(url,
        McpIntegrations.requestTimeout(config, definition), Map.of(), Map.of(), null,
        McpIntegrations.mcpJsonMapper(mapper));
    return McpIntegrations.toProvider(definition, transport, ToolSourceKind.REMOTE_HTTP);
  }
}
