// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.integration;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the shared {@code requestTimeout} and header-map parsing used by both MCP contributor
 * factories.
 */
class McpIntegrationsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void requestTimeout_defaultsTo30sWhenAbsent() throws Exception {
    assertThat(McpIntegrations.requestTimeout(config("{ \"command\": \"npx\" }"), definition()))
        .isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void requestTimeout_defaultsTo30sWhenNull() throws Exception {
    assertThat(McpIntegrations.requestTimeout(
        config("{ \"command\": \"npx\", \"requestTimeout\": null }"), definition()))
        .isEqualTo(McpIntegrations.DEFAULT_REQUEST_TIMEOUT);
  }

  @Test
  void requestTimeout_parsesIso8601DurationWhenPresent() throws Exception {
    assertThat(McpIntegrations.requestTimeout(
        config("{ \"command\": \"npx\", \"requestTimeout\": \"PT45S\" }"), definition()))
        .isEqualTo(Duration.ofSeconds(45));
  }

  @Test
  void requestTimeout_rejectsInvalidDurationNamingIntegration() throws Exception {
    JsonNode config = config("{ \"command\": \"npx\", \"requestTimeout\": \"45 seconds\" }");

    assertThatThrownBy(() -> McpIntegrations.requestTimeout(config, definition()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("github")
        .hasMessageContaining("requestTimeout");
  }

  @Test
  void stringMap_emptyWhenFieldAbsent() throws Exception {
    assertThat(McpIntegrations.stringMap(config("{ \"command\": \"npx\" }"), "staticHeaders",
        definition())).isEmpty();
  }

  @Test
  void stringMap_emptyWhenFieldIsNull() throws Exception {
    assertThat(McpIntegrations.stringMap(
        config("{ \"command\": \"npx\", \"staticHeaders\": null }"), "staticHeaders", definition()))
        .isEmpty();
  }

  @Test
  void stringMap_parsesAllEntries() throws Exception {
    JsonNode config = config("""
        { "command": "npx", "secretHeaders": { "Authorization": "TOKEN_REF", "X-Trace": "TRACE_REF" } }
        """);

    assertThat(McpIntegrations.stringMap(config, "secretHeaders", definition()))
        .containsOnly(Map.entry("Authorization", "TOKEN_REF"), Map.entry("X-Trace", "TRACE_REF"));
  }

  @Test
  void stringMap_rejectsNonObjectValueNamingIntegrationAndField() throws Exception {
    JsonNode config = config("{ \"command\": \"npx\", \"staticHeaders\": [\"not-an-object\"] }");

    assertThatThrownBy(() -> McpIntegrations.stringMap(config, "staticHeaders", definition()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("github")
        .hasMessageContaining("staticHeaders");
  }

  @Test
  void stringMap_rejectsNonStringEntryValueNamingIntegrationFieldAndKey() throws Exception {
    JsonNode config = config("{ \"command\": \"npx\", \"staticHeaders\": { \"X-Count\": 5 } }");

    assertThatThrownBy(() -> McpIntegrations.stringMap(config, "staticHeaders", definition()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("github")
        .hasMessageContaining("staticHeaders")
        .hasMessageContaining("X-Count");
  }

  private static JsonNode config(String json) throws Exception {
    return MAPPER.readTree(json);
  }

  private static IntegrationDefinition definition() {
    return new IntegrationDefinition("github", "GitHub", IntegrationType.MCP_STDIO,
        "{ \"command\": \"npx\" }", true);
  }
}
