package com.agentforge4j.mcp.integration;

import com.agentforge4j.core.spi.integration.IntegrationCapability;
import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.SecretResolver;
import com.agentforge4j.core.spi.integration.ToolProviderFactoryContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.mcp.client.McpToolProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStreamableHttpToolProviderFactoryTest {

  private final McpStreamableHttpToolProviderFactory factory =
      new McpStreamableHttpToolProviderFactory();
  private final SecretResolver secretResolver = reference -> reference;
  private final ToolProviderFactoryContext context =
      new ToolProviderFactoryContext(new ObjectMapper(), secretResolver);

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
        IntegrationType.MCP_STDIO, "{ \"command\": \"npx\" }",
        List.of(new IntegrationCapability("github.create_issue", null, false)), true);

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("github")
        .hasMessageContaining("MCP_STREAMABLE_HTTP");
  }

  private static IntegrationDefinition definition(String id, String config) {
    return new IntegrationDefinition(id, "Jira", IntegrationType.MCP_STREAMABLE_HTTP, config,
        List.of(new IntegrationCapability("jira.create_issue", "create_issue", true)), true);
  }
}
