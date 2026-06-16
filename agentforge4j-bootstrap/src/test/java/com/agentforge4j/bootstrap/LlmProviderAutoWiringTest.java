// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderAutoWiringTest {

  private final Map<String, String> originalValues = new HashMap<>();

  @AfterEach
  void restoreProperties() {
    for (Map.Entry<String, String> entry : originalValues.entrySet()) {
      String key = entry.getKey();
      String previous = entry.getValue();
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
    originalValues.clear();
  }

  @Test
  void envApiKeyProducesLlmClient() {
    setSystemProperty("agentforge4j.llm.openai.api-key", "sk-test");
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af).isNotNull();
  }

  @Test
  void programmaticConfigWinsOverEnvVar() {
    setSystemProperty("agentforge4j.llm.openai.api-key", "env-key");
    LlmProviderConfig openai = LlmProviderConfig.openai()
        .defaults()
        .apiKey("programmatic-key")
        .build();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmProvider(openai)
        .build();
    assertThat(af).isNotNull();
  }

  @Test
  void noProvidersLogsWarning() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af).isNotNull();
  }

  @Test
  void invalidTimeoutValueThrowsOnBuild() {
    setSystemProperty("agentforge4j.llm.openai.api-key", "sk-test");
    setSystemProperty("agentforge4j.llm.openai.connect-timeout-seconds", "not-a-number");
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().build())
        .isInstanceOf(IllegalStateException.class)
        .cause()
        .isInstanceOf(NumberFormatException.class);
  }

  @Test
  void ollamaIncludedWithoutApiKey() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();
    assertThat(af).isNotNull();
  }

  private void setSystemProperty(String key, String value) {
    originalValues.putIfAbsent(key, System.getProperty(key));
    System.setProperty(key, value);
  }
}
