package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentforge4j.core.spi.integration.IntegrationCapability;
import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolProviderResolver;
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

/**
 * Covers the bootstrap integrations chain: integrations dir → filesystem loader → in-memory
 * repository → tool provider factory → capability resolver, plus the escape hatches, the
 * coexistence of integrations with pre-built providers, and the shared-capability fail-fast.
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
        new ToolSource("http:tickets", "create"))));

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
        new ToolSource("mcp:github", "create_issue"))));

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withToolProviders(List.of(mcpProvider))
        .build();

    ResolvedTool resolved = af.components().toolProviderResolver()
        .resolve("github.create_issue", new ToolScope("wf", "run"));
    assertThat(resolved.provider()).isSameAs(mcpProvider);
    assertThat(af.components().integrationRepository()).isNull();
  }

  @Test
  void integrationsAndExplicitToolProviderSharingACapabilityFailFast() throws IOException {
    writeGithubDefinition(true);
    RecordingToolProviderFactory factory = new RecordingToolProviderFactory();
    ToolProvider preBuilt = mock(ToolProvider.class);
    when(preBuilt.providerId()).thenReturn("http:dup");
    when(preBuilt.listTools()).thenReturn(List.of(new ToolDescriptor(
        "github.create_issue", "github.create_issue", null, null, null,
        new ToolSource("http:dup", "create_issue"))));

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
          "config": { "command": "npx" },
          "capabilities": [
            { "capability": "github.create_issue", "remoteToolName": "create_issue" }
          ]
        }
        """.formatted(active));
  }

  private static IntegrationDefinition githubDefinition() {
    return new IntegrationDefinition("github", "GitHub", IntegrationType.MCP_STDIO,
        "{ \"command\": \"npx\" }",
        List.of(new IntegrationCapability("github.create_issue", "create_issue", false)), true);
  }

  /**
   * {@link ToolProviderFactory} fake that records the definitions it realises and hands back a
   * stub provider exposing one descriptor per definition capability.
   */
  private static final class RecordingToolProviderFactory implements ToolProviderFactory {

    private final List<IntegrationDefinition> created = new ArrayList<>();

    @Override
    public ToolProvider create(IntegrationDefinition definition) {
      created.add(definition);
      ToolProvider provider = mock(ToolProvider.class);
      List<ToolDescriptor> descriptors = definition.capabilities().stream()
          .map(capability -> new ToolDescriptor(capability.capability(), null, null, null, null,
              new ToolSource("mcp:%s".formatted(definition.id()), capability.remoteToolName())))
          .toList();
      when(provider.listTools()).thenReturn(descriptors);
      return provider;
    }
  }
}
