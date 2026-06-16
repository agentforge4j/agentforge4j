// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.tools.http;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.SecretResolver;
import com.agentforge4j.core.spi.integration.ToolProviderFactoryContext;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.tools.http.LoopbackHttpServer.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the config-loaded HTTP path: an {@code HTTP_TOOL} {@link IntegrationDefinition} parsed by
 * {@link HttpToolProviderFactory} into a working {@link HttpToolProvider}, including secret-header resolution through
 * the {@link ToolProviderFactoryContext} resolver. The code-defined path is covered by {@link HttpToolProviderTest};
 * this class exercises only the factory seam.
 */
class HttpToolProviderFactoryTest {

  private final HttpToolProviderFactory factory = new HttpToolProviderFactory();
  private final ObjectMapper mapper = new ObjectMapper();
  private final SecretResolver secretResolver = reference ->
      "WEATHER_TOKEN".equals(reference) ? "Bearer resolved-token" : null;
  private final ToolProviderFactoryContext context =
      new ToolProviderFactoryContext(mapper, secretResolver);
  private final ToolInvocationContext invocation =
      new ToolInvocationContext("run-1", "1", "agent-1", new ToolScope("wf-1", "run-1"));
  private final ToolExecutionOptions noRetry =
      new ToolExecutionOptions(Duration.ofSeconds(5), 0, Duration.ZERO);

  @Test
  void supportedType_isHttpTool() {
    assertThat(factory.supportedType()).isEqualTo(IntegrationType.HTTP_TOOL);
  }

  @Test
  void create_buildsProviderWithHttpPrefixedProviderIdAndListsCapabilities() {
    ToolProvider provider = factory.create(definition("weather", endpointConfig("https://x/{city}")),
        context);

    assertThat(provider.providerId()).isEqualTo("http:weather");
    assertThat(provider.listTools()).hasSize(1);
    ToolDescriptor descriptor = provider.listTools().get(0);
    assertThat(descriptor.capability()).isEqualTo("weather.get_current");
    assertThat(descriptor.source().providerId()).isEqualTo("http:weather");
  }

  @Test
  void create_endpointOmittingMutatingDefaultsToConservativeRiskSignal() {
    // The factory parses config JSON directly (no schema gate on this seam), so an endpoint that
    // omits `mutating` must still surface the highest safe risk rather than the primitive-false.
    ToolProvider provider = factory.create(definition("weather", endpointConfig("https://x/{city}")),
        context);

    assertThat(provider.listTools().get(0).riskMetadata().mutating()).isTrue();
  }

  @Test
  void create_providerInvokesEndpointResolvingTheSecretHeaderAtInvoke() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(Response.json(200, "{\"ok\":true}"))) {
      ToolProvider provider = factory.create(
          definition("weather", endpointConfig(server.baseUri() + "/weather/{city}")), context);
      ToolDescriptor descriptor = provider.listTools().get(0);

      ToolResult result = provider.invoke(descriptor, "{\"city\":\"london\"}", invocation, noRetry);

      assertThat(result.success()).isTrue();
      assertThat(result.output()).isEqualTo("{\"ok\":true}");
      assertThat(server.captured().get(0).target()).isEqualTo("/weather/london");
      assertThat(server.captured().get(0).headers())
          .containsEntry("Authorization", "Bearer resolved-token");
    }
  }

  @Test
  void create_failsFastNamingTheIntegrationOnMalformedConfig() {
    assertThatThrownBy(() -> factory.create(definition("weather", "[ { not valid json"), context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("weather");
  }

  @Test
  void create_rejectsADefinitionOfTheWrongType() {
    IntegrationDefinition mcp = new IntegrationDefinition("github", "GitHub",
        IntegrationType.MCP_STDIO, "{}", true);

    assertThatThrownBy(() -> factory.create(mcp, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HTTP_TOOL");
  }

  private static IntegrationDefinition definition(String id, String config) {
    return new IntegrationDefinition(id, id, IntegrationType.HTTP_TOOL, config, true);
  }

  private static String endpointConfig(String urlTemplate) {
    return """
        [
          {
            "capability": "weather.get_current",
            "method": "GET",
            "urlTemplate": "%s",
            "inputSchema": {
              "type": "object",
              "additionalProperties": false,
              "properties": { "city": { "type": "string" } }
            },
            "bodyMode": "NONE",
            "secretHeaders": { "Authorization": "WEATHER_TOKEN" }
          }
        ]
        """.formatted(urlTemplate);
  }
}
