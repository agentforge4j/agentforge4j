package com.agentforge4j.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapModuleSmokeTest {

  @Test
  void moduleTypesLoadable() {
    assertThat(AgentForge4jBootstrap.class).isNotNull();
    assertThat(AgentForge4j.class).isNotNull();
  }
}
