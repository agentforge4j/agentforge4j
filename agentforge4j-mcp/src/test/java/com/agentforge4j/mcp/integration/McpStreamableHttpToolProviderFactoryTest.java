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
