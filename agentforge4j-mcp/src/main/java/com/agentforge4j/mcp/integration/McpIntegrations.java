// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.integration;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.mcp.client.McpServerConnection;
import com.agentforge4j.mcp.client.McpToolProvider;
import com.agentforge4j.mcp.client.transport.McpTransport;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * Shared mechanics for the MCP {@code IntegrationToolProviderFactory} contributions: config JSON
 * parsing with errors naming the integration id, and the definition-to-provider assembly with the
 * {@code "mcp:" + id} provider id convention (matching the starter's MCP auto-configuration).
 */
final class McpIntegrations {

  /**
   * Per-request timeout applied when the {@code config} payload omits {@code requestTimeout}. This
   * mirrors the starter's MCP default.
   */
  static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private McpIntegrations() {
  }

  /**
   * Parses the definition's {@code config} JSON text with the shared mapper.
   *
   * @param definition the integration whose config to parse
   * @param mapper     the shared Jackson mapper supplied by the factory context
   *
   * @return the parsed config tree
   *
   * @throws IllegalArgumentException if the config is not valid JSON, naming the integration id
   */
  static JsonNode parseConfig(IntegrationDefinition definition, ObjectMapper mapper) {
    try {
      return mapper.readTree(definition.config());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Integration '%s': malformed config JSON (%s)"
          .formatted(definition.id(), e.getOriginalMessage()), e);
    }
  }

  /**
   * Wraps the shared Jackson mapper as the MCP SDK's {@link McpJsonMapper} for transport
   * construction (the SDK's Jackson-2 binding; the project is Jackson 2).
   *
   * @param mapper the shared Jackson mapper supplied by the factory context
   *
   * @return an {@link McpJsonMapper} backed by {@code mapper}
   */
  static McpJsonMapper mcpJsonMapper(ObjectMapper mapper) {
    return new JacksonMcpJsonMapper(mapper);
  }

  /**
   * Wraps a built transport into the provider for the definition, using the integration id as the
   * connection's server id and {@code "mcp:" + id} as the provider id.
   *
   * @param definition the integration being realised
   * @param transport  the transport built from the definition's config
   *
   * @return the tool provider over a fresh connection
   */
  static ToolProvider toProvider(IntegrationDefinition definition, McpTransport transport) {
    McpServerConnection connection = new McpServerConnection(definition.id(), transport);
    return new McpToolProvider("mcp:%s".formatted(definition.id()), connection);
  }

  /**
   * Reads the optional {@code requestTimeout} from the config tree. When absent or null, the
   * {@link #DEFAULT_REQUEST_TIMEOUT} applies. When present, it must be an ISO-8601 duration string
   * (for example {@code "PT45S"}), parsed via {@link Duration#parse(CharSequence)}.
   *
   * @param config     the parsed config tree
   * @param definition the integration, for the error message
   *
   * @return the configured per-request timeout, or {@link #DEFAULT_REQUEST_TIMEOUT} when omitted
   *
   * @throws IllegalArgumentException if {@code requestTimeout} is present but not a valid ISO-8601
   *                                  duration, naming the integration id
   */
  static Duration requestTimeout(JsonNode config, IntegrationDefinition definition) {
    JsonNode value = config.get("requestTimeout");
    if (value == null || value.isNull()) {
      return DEFAULT_REQUEST_TIMEOUT;
    }
    String text = value.asText();
    try {
      return Duration.parse(text);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          ("Integration '%s' (%s) has an invalid 'requestTimeout' '%s' in config "
              + "(expected an ISO-8601 duration such as PT30S)")
              .formatted(definition.id(), definition.type(), text), e);
    }
  }

  /**
   * Reads a required non-blank text field from the config tree.
   *
   * @param config     the parsed config tree
   * @param field      the field name
   * @param definition the integration, for the error message
   *
   * @return the non-blank field value
   *
   * @throws IllegalArgumentException if the field is absent, null, or blank, naming the integration
   *                                  id and field
   */
  static String requiredText(JsonNode config, String field, IntegrationDefinition definition) {
    JsonNode value = config.get(field);
    String text = (value == null || value.isNull()) ? null : value.asText();
    return Validate.notBlank(text,
        "Integration '%s' (%s) requires a non-blank '%s' in config"
            .formatted(definition.id(), definition.type(), field));
  }
}
