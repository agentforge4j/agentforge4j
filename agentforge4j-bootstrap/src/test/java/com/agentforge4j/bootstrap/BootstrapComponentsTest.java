package com.agentforge4j.bootstrap;

import com.agentforge4j.core.spi.integration.IntegrationCapability;
import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolProviderResolver;
import com.agentforge4j.runtime.tool.IntegrationToolProviderResolver;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BootstrapComponentsTest {

  @Test
  void componentsIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components()).isNotNull();
  }

  @Test
  void componentsAgentRepositoryIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().agentRepository()).isNotNull();
  }

  @Test
  void componentsWorkflowRepositoryIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().workflowRepository()).isNotNull();
  }

  @Test
  void componentsLlmClientResolverIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().llmClientResolver()).isNotNull();
  }

  @Test
  void componentsFileSinkIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().fileSink()).isNotNull();
  }

  @Test
  void componentsObjectMapperIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().objectMapper()).isNotNull();
  }

  @Test
  void componentsClockIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().clock()).isNotNull();
  }

  @Test
  void componentsLoadedConfigurationIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().loadedConfiguration()).isNotNull();
  }

  @Test
  void componentsWorkflowStateRepositoryIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().workflowStateRepository()).isNotNull();
  }

  @Test
  void componentsWorkflowEventLogIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().workflowEventLog()).isNotNull();
  }

  @Test
  void componentsContextRendererIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().contextRenderer()).isNotNull();
  }

  @Test
  void componentsLlmCommandParserIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().llmCommandParser()).isNotNull();
  }

  @Test
  void componentsEventRecorderIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().eventRecorder()).isNotNull();
  }

  @Test
  void componentsLlmProviderSelectionStrategyIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().llmProviderSelectionStrategy()).isNotNull();
  }

  @Test
  void componentsAgentInvokerIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().agentInvoker()).isNotNull();
  }

  @Test
  void componentsLlmCallObserverIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().llmCallObserver()).isNotNull();
  }

  @Test
  void componentsIntegrationRepositoryIsNullWithoutToolSupport() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().integrationRepository()).isNull();
  }

  @Test
  void componentsToolProviderResolverIsNullWithoutToolSupport() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().toolProviderResolver()).isNull();
  }

  @Test
  void componentsExposeIntegrationRepositoryAndResolverOnIntegrationsPath() {
    IntegrationDefinition definition = new IntegrationDefinition("github", "GitHub",
        IntegrationType.MCP_STDIO, "{ \"command\": \"npx\" }",
        List.of(new IntegrationCapability("github.create_issue", "create_issue", false)), true);
    ToolProvider provider = mock(ToolProvider.class);
    when(provider.listTools()).thenReturn(List.of());

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withIntegrationConfigLoader(() -> List.of(definition))
        .withToolProviderFactory(ignored -> provider)
        .build();

    assertThat(af.components().integrationRepository()).isNotNull();
    assertThat(af.components().integrationRepository().findById("github")).isEqualTo(definition);
    assertThat(af.components().toolProviderResolver())
        .isInstanceOf(IntegrationToolProviderResolver.class);
  }

  @Test
  void componentsExposeExplicitResolverButNoRepository() {
    ToolProviderResolver resolver = mock(ToolProviderResolver.class);

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withToolProviderResolver(resolver)
        .build();

    assertThat(af.components().toolProviderResolver()).isSameAs(resolver);
    assertThat(af.components().integrationRepository()).isNull();
  }

  @Test
  void componentsExposeResolverButNoRepositoryWithPreBuiltProvidersOnly() {
    ToolProvider provider = mock(ToolProvider.class);
    when(provider.providerId()).thenReturn("mcp:x");
    when(provider.listTools()).thenReturn(List.of());

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withToolProviders(List.of(provider))
        .build();

    assertThat(af.components().toolProviderResolver())
        .isInstanceOf(IntegrationToolProviderResolver.class);
    assertThat(af.components().integrationRepository()).isNull();
  }
}
