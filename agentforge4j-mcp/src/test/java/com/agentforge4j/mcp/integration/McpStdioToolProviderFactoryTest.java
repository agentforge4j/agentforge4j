// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.integration;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.SecretResolver;
import com.agentforge4j.core.spi.integration.ToolProviderFactoryContext;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.mcp.client.McpToolProvider;
import com.agentforge4j.util.net.HttpEgressGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStdioToolProviderFactoryTest {

  private final McpStdioToolProviderFactory factory = new McpStdioToolProviderFactory();
  private final SecretResolver secretResolver = reference -> reference;
  private final ToolProviderFactoryContext context =
      new ToolProviderFactoryContext(new ObjectMapper(), secretResolver, new HttpEgressGuard(false));

  @Test
  void supportedType_isMcpStdio() {
    assertThat(factory.supportedType()).isEqualTo(IntegrationType.MCP_STDIO);
  }

  @Test
  void create_buildsMcpToolProviderWithMcpPrefixedProviderId() {
    IntegrationDefinition definition = definition("github", """
        {
          "command": "npx",
          "args": ["-y", "@modelcontextprotocol/server-github"],
          "env": { "GITHUB_TOKEN": "placeholder" }
        }
        """);

    ToolProvider provider = factory.create(definition, context);

    assertThat(provider).isInstanceOf(McpToolProvider.class);
    assertThat(provider.providerId()).isEqualTo("mcp:github");
  }

  @Test
  void create_doesNotLaunchTheProcessUntilTheProviderIsUsed() {
    // The stdio transport launches a subprocess only on lazy connect (first listTools/invoke).
    // Realising the integration must not implicitly spawn it — the connection is reported DOWN
    // ("not started") until used. This is the operator-trust regression guard for MCP stdio.
    IntegrationDefinition definition = definition("github", """
        { "command": "definitely-not-a-real-command-xyz" }
        """);

    ToolProvider provider = factory.create(definition, context);

    assertThat(provider.health().state()).isEqualTo(HealthStatus.State.DOWN);
  }

  @Test
  void create_buildsProviderWhenArgsAndEnvAreOmitted() {
    IntegrationDefinition definition = definition("github", """
        { "command": "npx" }
        """);

    assertThat(factory.create(definition, context).providerId()).isEqualTo("mcp:github");
  }

  @Test
  void create_buildsProviderWhenRequestTimeoutIsConfigured() {
    IntegrationDefinition definition = definition("github", """
        { "command": "npx", "requestTimeout": "PT45S" }
        """);

    assertThat(factory.create(definition, context).providerId()).isEqualTo("mcp:github");
  }

  @Test
  void create_failsOnInvalidRequestTimeoutNamingIntegration() {
    IntegrationDefinition definition = definition("github", """
        { "command": "npx", "requestTimeout": "45 seconds" }
        """);

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("github")
        .hasMessageContaining("requestTimeout");
  }

  @Test
  void create_failsWhenCommandIsMissingNamingIntegrationAndField() {
    IntegrationDefinition definition = definition("github", """
        { "args": ["-y"] }
        """);

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("github")
        .hasMessageContaining("command");
  }

  @Test
  void create_failsOnMalformedConfigNamingIntegration() {
    IntegrationDefinition definition = definition("github", "{ not json");

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("github")
        .hasMessageContaining("malformed config JSON");
  }

  @Test
  void create_rejectsDefinitionOfAnotherType() {
    IntegrationDefinition definition = new IntegrationDefinition("jira", "Jira",
        IntegrationType.MCP_STREAMABLE_HTTP, "{ \"url\": \"https://mcp.example.com\" }", true);

    assertThatThrownBy(() -> factory.create(definition, context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jira")
        .hasMessageContaining("MCP_STDIO");
  }

  private static IntegrationDefinition definition(String id, String config) {
    return new IntegrationDefinition(id, "GitHub", IntegrationType.MCP_STDIO, config, true);
  }
}
