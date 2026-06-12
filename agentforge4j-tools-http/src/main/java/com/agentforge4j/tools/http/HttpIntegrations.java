package com.agentforge4j.tools.http;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Shared mechanics for the HTTP {@link com.agentforge4j.core.spi.integration.IntegrationToolProviderFactory}
 * contribution: parsing the {@code config} JSON array into {@link HttpEndpointDefinition}s with errors naming the
 * integration id (mirroring the MCP {@code McpIntegrations} helper). Per-field and cross-field validation lives in
 * {@link HttpEndpointDefinition} and {@link HttpToolProvider} respectively; this helper only turns config text into
 * definitions.
 */
final class HttpIntegrations {

  private HttpIntegrations() {
  }

  /**
   * Parses the definition's {@code config} JSON — an array of endpoint objects — into endpoint definitions with the
   * shared mapper.
   *
   * @param definition the HTTP_TOOL integration whose config to parse
   * @param mapper     the shared Jackson mapper supplied by the factory context
   *
   * @return the parsed endpoint definitions
   *
   * @throws IllegalArgumentException if the config is not a valid endpoint array, naming the integration id
   */
  static List<HttpEndpointDefinition> parseEndpoints(IntegrationDefinition definition, ObjectMapper mapper) {
    try {
      return mapper.readValue(definition.config(),
          mapper.getTypeFactory().constructCollectionType(List.class, HttpEndpointDefinition.class));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Integration '%s': malformed HTTP_TOOL config (%s)"
          .formatted(definition.id(), e.getOriginalMessage()), e);
    }
  }
}
