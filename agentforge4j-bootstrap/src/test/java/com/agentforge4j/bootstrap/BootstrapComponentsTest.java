package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
  void componentsIntegrationRegistryIsNotNull() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af.components().integrationRegistry()).isNotNull();
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
}
