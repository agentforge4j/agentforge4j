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
}
