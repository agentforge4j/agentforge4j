// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmProviderConfigurationException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bootstrap-facade smoke tests for LLM provider auto-wiring. No provider modules are on the test
 * classpath, so detailed neutral-config / options / fail-fast assertions (with captor factories) live
 * in {@link LlmClientWiringTest}; these confirm {@link AgentForge4jBootstrap} builds end-to-end.
 */
class LlmProviderAutoWiringTest {

  private final Map<String, String> originalValues = new HashMap<>();

  @AfterEach
  void restore() {
    for (Map.Entry<String, String> entry : originalValues.entrySet()) {
      if (entry.getValue() == null) {
        System.clearProperty(entry.getKey());
      } else {
        System.setProperty(entry.getKey(), entry.getValue());
      }
    }
    originalValues.clear();
  }

  @Test
  void buildsWithNoProvidersConfigured() {
    AgentForge4j af = AgentForge4jBootstrap.defaults().build();

    assertThat(af).isNotNull();
    assertThat(af.components().llmClientResolver()).isNotNull();
  }

  @Test
  void systemPropertyConfigForUnknownProviderFailsFast() {
    // No provider modules are on the bootstrap test classpath, so a system-property-configured
    // provider has no factory and must fail fast (not be silently ignored).
    setProperty("agentforge4j.llm.openai.api.key", "sk-test");
    setProperty("agentforge4j.llm.openai.base.url", "https://api.openai.com/v1/responses");

    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().build())
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("openai");
  }

  private void setProperty(String key, String value) {
    originalValues.putIfAbsent(key, System.getProperty(key));
    System.setProperty(key, value);
  }
}
