package com.agentforge4j.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.spi.tool.ToolSource;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegrationToolProviderResolverTest {

  private final ToolScope scope = new ToolScope("wf", "run");

  /**
   * Realises each definition as a provider whose single realised tool's capability is carried
   * verbatim in {@code config} — the resolver indexes the realised set, never any declared envelope.
   */
  private final ToolProviderFactory factory =
      definition -> provider("mcp:" + definition.id(), definition.config());

  @Test
  void resolvesCapabilityFromActiveIntegration() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", true, "github.create_pull_request"));
    IntegrationToolProviderResolver resolver =
        new IntegrationToolProviderResolver(repository, factory, List.of());

    ResolvedTool resolved = resolver.resolve("github.create_pull_request", scope);

    assertThat(resolved.provider().providerId()).isEqualTo("mcp:github");
    assertThat(resolved.descriptor().capability()).isEqualTo("github.create_pull_request");
  }

  @Test
  void duplicateCapabilityAcrossIntegrationsFailsFastNamingBoth() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", true, "github.create_pull_request"));
    repository.save(definition("github-mirror", true, "github.create_pull_request"));

    assertThatThrownBy(() -> new IntegrationToolProviderResolver(repository, factory, List.of()))
        .isInstanceOf(CapabilityResolutionException.class)
        .hasMessageContaining("github.create_pull_request")
        .hasMessageContaining("github")
        .hasMessageContaining("github-mirror");
  }

  @Test
  void unknownCapabilityFailsFast() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", true, "github.create_pull_request"));
    IntegrationToolProviderResolver resolver =
        new IntegrationToolProviderResolver(repository, factory, List.of());

    assertThatThrownBy(() -> resolver.resolve("jira.create_issue", scope))
        .isInstanceOf(CapabilityResolutionException.class)
        .hasMessageContaining("jira.create_issue");
  }

  @Test
  void inactiveIntegrationIsExcludedFromTheIndex() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", true, "github.create_pull_request"));
    repository.save(definition("jira", false, "jira.create_issue"));
    IntegrationToolProviderResolver resolver =
        new IntegrationToolProviderResolver(repository, factory, List.of());

    assertThat(resolver.resolve("github.create_pull_request", scope).descriptor().capability())
        .isEqualTo("github.create_pull_request");
    assertThatThrownBy(() -> resolver.resolve("jira.create_issue", scope))
        .isInstanceOf(CapabilityResolutionException.class)
        .hasMessageContaining("jira.create_issue");
    assertThat(resolver.available(scope))
        .extracting(ToolDescriptor::capability)
        .containsExactly("github.create_pull_request");
  }

  @Test
  void resolvesCapabilityFromPreBuiltProvider() {
    IntegrationToolProviderResolver resolver = new IntegrationToolProviderResolver(
        new InMemoryIntegrationRepository(), factory,
        List.of(preBuilt("mcp:a", "github.create_pull_request")));

    ResolvedTool resolved = resolver.resolve("github.create_pull_request", scope);

    assertThat(resolved.provider().providerId()).isEqualTo("mcp:a");
    assertThat(resolved.descriptor().capability()).isEqualTo("github.create_pull_request");
  }

  @Test
  void mergesDisjointCapabilitiesFromRepositoryAndPreBuiltProviders() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", true, "github.create_pull_request"));
    IntegrationToolProviderResolver resolver = new IntegrationToolProviderResolver(
        repository, factory, List.of(preBuilt("http:tickets", "tickets.create")));

    assertThat(resolver.resolve("github.create_pull_request", scope).provider().providerId())
        .isEqualTo("mcp:github");
    assertThat(resolver.resolve("tickets.create", scope).provider().providerId())
        .isEqualTo("http:tickets");
    assertThat(resolver.available(scope))
        .extracting(ToolDescriptor::capability)
        .containsExactlyInAnyOrder("github.create_pull_request", "tickets.create");
  }

  @Test
  void duplicateCapabilityAcrossRepositoryAndPreBuiltProviderFailsFastNamingBoth() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", true, "github.create_pull_request"));

    assertThatThrownBy(() -> new IntegrationToolProviderResolver(repository, factory,
        List.of(preBuilt("mcp:mirror", "github.create_pull_request"))))
        .isInstanceOf(CapabilityResolutionException.class)
        .hasMessageContaining("github.create_pull_request")
        .hasMessageContaining("integration 'github'")
        .hasMessageContaining("provider 'mcp:mirror'");
  }

  @Test
  void duplicateCapabilityAcrossTwoPreBuiltProvidersFailsFastNamingBoth() {
    assertThatThrownBy(() -> new IntegrationToolProviderResolver(
        new InMemoryIntegrationRepository(), factory,
        List.of(preBuilt("mcp:a", "github.create_pull_request"),
            preBuilt("mcp:b", "github.create_pull_request"))))
        .isInstanceOf(CapabilityResolutionException.class)
        .hasMessageContaining("provider 'mcp:a'")
        .hasMessageContaining("provider 'mcp:b'");
  }

  @Test
  void emptyRepositoryAndNoPreBuiltProvidersResolveNothing() {
    IntegrationToolProviderResolver resolver = new IntegrationToolProviderResolver(
        new InMemoryIntegrationRepository(), factory, List.of());

    assertThat(resolver.available(scope)).isEmpty();
    assertThatThrownBy(() -> resolver.resolve("github.create_pull_request", scope))
        .isInstanceOf(CapabilityResolutionException.class);
  }

  private static ToolProvider preBuilt(String providerId, String capability) {
    return provider(providerId, capability);
  }

  private static IntegrationDefinition definition(String id, boolean active, String capability) {
    // The fake factory realises one tool whose capability is carried verbatim in config.
    return new IntegrationDefinition(id, id, IntegrationType.MCP_STDIO, capability, active);
  }

  private static ToolProvider provider(String providerId, String... capabilities) {
    List<ToolDescriptor> descriptors = Arrays.stream(capabilities)
        .map(capability -> new ToolDescriptor(capability, capability,
            null, null, null, new ToolSource(providerId, capability),
            ToolRiskMetadata.conservative()))
        .toList();
    return new ToolProvider() {
      @Override
      public String providerId() {
        return providerId;
      }

      @Override
      public List<ToolDescriptor> listTools() {
        return descriptors;
      }

      @Override
      public ToolResult invoke(ToolDescriptor descriptor, String arguments,
          ToolInvocationContext ctx, ToolExecutionOptions options) {
        return ToolResult.success(null, 0L);
      }

      @Override
      public HealthStatus health() {
        return new HealthStatus(HealthStatus.State.UP, null);
      }
    };
  }
}
