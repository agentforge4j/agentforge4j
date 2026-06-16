// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolProviderResolver;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.runtime.tool.InMemoryIntegrationRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the bootstrap integrations chain: integrations dir → filesystem loader → in-memory repository → tool provider
 * factory → capability resolver, plus the escape hatches, the coexistence of integrations with pre-built providers, and
 * the shared-capability fail-fast.
 */
class IntegrationWiringTest {

  @TempDir
  Path integrationsDir;

  @Test
  void withIntegrationsDirLoadsDefinitionsThroughFactoryIntoToolSupport() throws IOException {
    writeGithubDefinition(true);
    RecordingToolProviderFactory factory = new RecordingToolProviderFactory();

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withIntegrationsDir(integrationsDir)
        .withToolProviderFactory(factory)
        .build();

    assertThat(af).isNotNull();
    assertThat(factory.created).hasSize(1);
    assertThat(factory.created.get(0).id()).isEqualTo("github");
    assertThat(factory.created.get(0).type()).isEqualTo(IntegrationType.MCP_STDIO);
  }

  @Test
  void withIntegrationsDirOnEmptyDirectoryBuildsWithoutMaterializingAnything() {
    RecordingToolProviderFactory factory = new RecordingToolProviderFactory();

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withIntegrationsDir(integrationsDir)
        .withToolProviderFactory(factory)
        .build();

    assertThat(af).isNotNull();
    assertThat(factory.created).isEmpty();
  }

  @Test
  void inactiveDefinitionLoadsButIsNotMaterialized() throws IOException {
    writeGithubDefinition(false);
    RecordingToolProviderFactory factory = new RecordingToolProviderFactory();

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withIntegrationsDir(integrationsDir)
        .withToolProviderFactory(factory)
        .build();

    assertThat(af).isNotNull();
    assertThat(factory.created).isEmpty();
  }

  @Test
  void withIntegrationConfigLoaderOverridesTheFilesystemLoader() {
    IntegrationDefinition definition = githubDefinition();
    RecordingToolProviderFactory factory = new RecordingToolProviderFactory();

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withIntegrationConfigLoader(() -> List.of(definition))
        .withToolProviderFactory(factory)
        .build();

    assertThat(af).isNotNull();
    assertThat(factory.created).containsExactly(definition);
  }

  @Test
  void withIntegrationRepositoryFeedsTheResolverWithoutALoader() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(githubDefinition());
    RecordingToolProviderFactory factory = new RecordingToolProviderFactory();

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withIntegrationRepository(repository)
        .withToolProviderFactory(factory)
        .build();

    assertThat(af).isNotNull();
    assertThat(factory.created).hasSize(1);
    assertThat(factory.created.get(0).id()).isEqualTo("github");
  }

  @Test
  void integrationsAndExplicitToolProvidersWithDisjointCapabilitiesCoexist() throws IOException {
    writeGithubDefinition(true);
    RecordingToolProviderFactory factory = new RecordingToolProviderFactory();
    ToolProvider preBuilt = mock(ToolProvider.class);
    when(preBuilt.providerId()).thenReturn("http:tickets");
    when(preBuilt.listTools()).thenReturn(List.of(new ToolDescriptor(
        "tickets.create", "tickets.create", null, null, null,
        new ToolSource("http:tickets", "create"), ToolRiskMetadata.conservative())));

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withIntegrationsDir(integrationsDir)
        .withToolProviderFactory(factory)
        .withToolProviders(List.of(preBuilt))
        .build();

    assertThat(af).isNotNull();
    assertThat(factory.created).hasSize(1);
    assertThat(factory.created.get(0).id()).isEqualTo("github");
  }

  @Test
  void preBuiltProvidersResolveThroughTheUnifiedResolverWithoutAnyDefinition() {
    // Simulates the starter's MCP path: McpAutoConfiguration produces a List<ToolProvider> from
    // agentforge4j.mcp.servers, forwarded via withToolProviders; it must resolve through the single
    // resolver with no static IntegrationDefinition and no integration repository exposed.
    ToolProvider mcpProvider = mock(ToolProvider.class);
    when(mcpProvider.providerId()).thenReturn("mcp:github");
    when(mcpProvider.listTools()).thenReturn(List.of(new ToolDescriptor(
        "github.create_issue", "github.create_issue", null, null, null,
        new ToolSource("mcp:github", "create_issue"), ToolRiskMetadata.conservative())));

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withToolProviders(List.of(mcpProvider))
        .build();

    ResolvedTool resolved = af.components().toolProviderResolver()
        .resolve("github.create_issue", new ToolScope("wf", "run"));
    assertThat(resolved.provider()).isSameAs(mcpProvider);
    assertThat(af.components().integrationRepository()).isNull();
  }

  @Test
  void httpToolIntegrationIsDiscoveredAndResolvesWithoutAnExplicitFactory() throws IOException {
    // No withToolProviderFactory: the real ServiceLoader aggregator must discover the
    // HttpToolProviderFactory (agentforge4j-tools-http is a test-scope dependency here), realise the
    // HTTP_TOOL definition through the shipped mapper (ISO-8601 timeout included), and resolve the
    // capability — i.e. the missing-contributor fail-fast no longer fires for HTTP_TOOL.
    Files.writeString(integrationsDir.resolve("airtable.json"), """
        {
          "id": "airtable",
          "displayName": "Airtable",
          "type": "HTTP_TOOL",
          "config": [
            {
              "capability": "airtable.list_records",
              "mutating": false,
              "method": "GET",
              "urlTemplate": "https://api.airtable.com/v0/{baseId}",
              "inputSchema": {
                "type": "object",
                "properties": { "baseId": { "type": "string" } }
              },
              "bodyMode": "NONE",
              "timeout": "PT5S",
              "secretHeaders": { "Authorization": "AIRTABLE_TOKEN" }
            }
          ]
        }
        """);

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withIntegrationsDir(integrationsDir)
        .build();

    ResolvedTool resolved = af.components().toolProviderResolver()
        .resolve("airtable.list_records", new ToolScope("wf", "run"));
    assertThat(resolved.provider().providerId()).isEqualTo("http:airtable");
  }

  @Test
  void integrationsAndExplicitToolProviderSharingACapabilityFailFast() throws IOException {
    writeGithubDefinition(true);
    RecordingToolProviderFactory factory = new RecordingToolProviderFactory();
    ToolProvider preBuilt = mock(ToolProvider.class);
    when(preBuilt.providerId()).thenReturn("http:dup");
    when(preBuilt.listTools()).thenReturn(List.of(new ToolDescriptor(
        "github.create_issue", "github.create_issue", null, null, null,
        new ToolSource("http:dup", "create_issue"), ToolRiskMetadata.conservative())));

    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults()
        .withIntegrationsDir(integrationsDir)
        .withToolProviderFactory(factory)
        .withToolProviders(List.of(preBuilt))
        .build())
        .isInstanceOf(CapabilityResolutionException.class)
        .hasMessageContaining("github.create_issue")
        .hasMessageContaining("integration 'github'")
        .hasMessageContaining("provider 'http:dup'");
  }

  @Test
  void explicitToolProviderResolverIsTheSoleResolver() throws IOException {
    writeGithubDefinition(true);
    RecordingToolProviderFactory factory = new RecordingToolProviderFactory();

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withIntegrationsDir(integrationsDir)
        .withToolProviderFactory(factory)
        .withToolProviderResolver(mock(ToolProviderResolver.class))
        .build();

    assertThat(af).isNotNull();
    assertThat(factory.created).isEmpty();
  }

  @Test
  void withIntegrationsDirNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withIntegrationsDir(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withIntegrationsDirMissingDirectoryThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults()
        .withIntegrationsDir(integrationsDir.resolve("does-not-exist")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withIntegrationConfigLoaderNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withIntegrationConfigLoader(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withIntegrationRepositoryNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withIntegrationRepository(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withToolProviderFactoryNullThrowsImmediatelyBeforeBuild() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withToolProviderFactory(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void writeGithubDefinition(boolean active) throws IOException {
    Files.writeString(integrationsDir.resolve("github.json"), """
        {
          "id": "github",
          "displayName": "GitHub",
          "type": "MCP_STDIO",
          "active": %s,
          "config": { "command": "npx" }
        }
        """.formatted(active));
  }

  private static IntegrationDefinition githubDefinition() {
    return new IntegrationDefinition("github", "GitHub", IntegrationType.MCP_STDIO,
        "{ \"command\": \"npx\" }", true);
  }

  /**
   * {@link ToolProviderFactory} fake that records the definitions it realises and hands back a stub
   * provider exposing the realised GitHub MCP tool. Every definition realised in this test is the
   * GitHub MCP server, whose realised capability is {@code github.create_issue} (capability == remote
   * tool name in the OSS MCP mapping).
   */
  private static final class RecordingToolProviderFactory implements ToolProviderFactory {

    private final List<IntegrationDefinition> created = new ArrayList<>();

    @Override
    public ToolProvider create(IntegrationDefinition definition) {
      created.add(definition);
      ToolProvider provider = mock(ToolProvider.class);
      when(provider.listTools()).thenReturn(List.of(new ToolDescriptor(
          "github.create_issue", "github.create_issue", null, null, null,
          new ToolSource("mcp:%s".formatted(definition.id()), "create_issue"),
          ToolRiskMetadata.conservative())));
      return provider;
    }
  }
}
