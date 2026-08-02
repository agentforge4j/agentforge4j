// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.integration;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.SecretResolver;
import com.agentforge4j.core.spi.integration.ToolProviderFactoryContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.mcp.client.McpToolProvider;
import com.agentforge4j.util.net.HttpEgressGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStreamableHttpToolProviderFactoryTest {

  private final McpStreamableHttpToolProviderFactory factory =
      new McpStreamableHttpToolProviderFactory();
  private final SecretResolver secretResolver = reference -> reference;
  // allowPrivateNetworks=true so the example URLs in the construction tests skip DNS resolution.
  private final ToolProviderFactoryContext context =
      new ToolProviderFactoryContext(new ObjectMapper(), secretResolver, new HttpEgressGuard(true));
  private final ToolProviderFactoryContext blockingContext =
      new ToolProviderFactoryContext(new ObjectMapper(), secretResolver, new HttpEgressGuard(false));

  @Test
  void supportedType_isMcpStreamableHttp() {
    assertThat(factory.supportedType()).isEqualTo(IntegrationType.MCP_STREAMABLE_HTTP);
  }

  @Test
  void create_buildsMcpToolProviderWithMcpPrefixedProviderId() {
    IntegrationDefinition definition = definition("jira", """
        { "url": "https://mcp.example.com/jira" }
        """);

    ToolProvider provider = factory.create(definition, context);

    assertThat(provider).isInstanceOf(McpToolProvider.class);
    assertThat(provider.providerId()).isEqualTo("mcp:jira");
  }

  @Test
  void create_deniesAServerUrlResolvingToACloudMetadataAddress() {
    IntegrationDefinition definition = definition("jira", """
        { "url": "http://169.254.169.254/mcp" }
        """);

    assertThatThrownBy(() -> factory.create(definition, blockingContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("169.254.169.254")
        .hasMessageContaining("egress");
  }

  @Test
  void create_failsWhenUrlIsMissingNamingIntegrationAndField() {
    IntegrationDefinition definition = definition("jira", "{}");

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jira")
        .hasMessageContaining("url");
  }

  @Test
  void create_failsOnMalformedConfigNamingIntegration() {
    IntegrationDefinition definition = definition("jira", "{ not json");

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jira")
        .hasMessageContaining("malformed config JSON");
  }

  @Test
  void create_buildsProviderWhenStaticAndSecretHeadersAreConfigured() {
    // Proves the config's header maps and the context's secret resolver actually reach
    // StreamableHttpTransport: if they were dropped (passed as Map.of()/null as before this fix),
    // this would still succeed vacuously, but the rejection tests below would not, since the
    // transport's own construction-time header validation would never see the values.
    IntegrationDefinition definition = definition("jira", """
        {
          "url": "https://mcp.example.com/jira",
          "staticHeaders": { "X-Api-Version": "2024-01" },
          "secretHeaders": { "Authorization": "JIRA_TOKEN" }
        }
        """);

    ToolProvider provider = factory.create(definition, context);

    assertThat(provider.providerId()).isEqualTo("mcp:jira");
  }

  @Test
  void create_rejectsHeaderNamesDifferingOnlyByCaseProvingConfiguredHeadersReachTheTransport() {
    // StreamableHttpTransport eagerly rejects case-insensitive duplicate header names at
    // construction. This only fires if the parsed config's staticHeaders map actually reaches the
    // transport constructor — before this fix the factory always passed Map.of(), so this case could
    // never be triggered through the integration-config path.
    IntegrationDefinition definition = definition("jira", """
        {
          "url": "https://mcp.example.com/jira",
          "staticHeaders": { "X-Token": "a", "x-token": "b" }
        }
        """);

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate header name");
  }

  @Test
  void create_rejectsAHeaderDeclaredAsBothLiteralAndSecret() {
    IntegrationDefinition definition = definition("jira", """
        {
          "url": "https://mcp.example.com/jira",
          "staticHeaders": { "Authorization": "Bearer literal" },
          "secretHeaders": { "authorization": "JIRA_TOKEN" }
        }
        """);

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("both a literal and a secret-reference");
  }

  @Test
  void create_failsOnNonObjectStaticHeadersNamingIntegrationAndField() {
    IntegrationDefinition definition = definition("jira", """
        { "url": "https://mcp.example.com/jira", "staticHeaders": "not-an-object" }
        """);

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jira")
        .hasMessageContaining("staticHeaders");
  }

  @Test
  void create_rejectsDefinitionOfAnotherType() {
    IntegrationDefinition definition = new IntegrationDefinition("github", "GitHub",
        IntegrationType.MCP_STDIO, "{ \"command\": \"npx\" }", true);

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("github")
        .hasMessageContaining("MCP_STREAMABLE_HTTP");
  }

  private static IntegrationDefinition definition(String id, String config) {
    return new IntegrationDefinition(id, "Jira", IntegrationType.MCP_STREAMABLE_HTTP, config, true);
  }
}
