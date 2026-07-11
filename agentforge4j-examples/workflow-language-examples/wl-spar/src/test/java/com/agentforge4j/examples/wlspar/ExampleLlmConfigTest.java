// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlspar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ExampleLlmConfig}'s fake/real resolution and its strict parsing of the explicit
 * {@code agentforge4j.example.fake-llm} toggle. Each case drives resolution through the
 * highest-precedence source (a JVM system property), so the assertions are deterministic regardless of
 * the surrounding environment.
 */
class ExampleLlmConfigTest {

  private static final String API_KEY_PROP = "agentforge4j.example.llm.api-key";
  private static final String FAKE_LLM_PROP = "agentforge4j.example.fake-llm";

  @AfterEach
  void clearProperties() {
    System.clearProperty(API_KEY_PROP);
    System.clearProperty(FAKE_LLM_PROP);
  }

  @Test
  void resolvesToFakeWhenNoToggleAndNoApiKey() {
    System.clearProperty(API_KEY_PROP);
    System.clearProperty(FAKE_LLM_PROP);

    assertThat(ExampleLlmConfig.load().fakeLlm()).isTrue();
  }

  @Test
  void explicitTrueResolvesToFakeEvenWithApiKeyPresent() {
    System.setProperty(API_KEY_PROP, "sk-test-key");
    System.setProperty(FAKE_LLM_PROP, "true");

    assertThat(ExampleLlmConfig.load().fakeLlm()).isTrue();
  }

  @Test
  void explicitFalseResolvesToRealWhenApiKeyPresent() {
    System.setProperty(API_KEY_PROP, "sk-test-key");
    System.setProperty(FAKE_LLM_PROP, "false");

    assertThat(ExampleLlmConfig.load().fakeLlm()).isFalse();
  }

  @Test
  void explicitFalseWithBlankApiKeyFailsFast() {
    System.clearProperty(API_KEY_PROP);
    System.setProperty(FAKE_LLM_PROP, "false");

    assertThatThrownBy(ExampleLlmConfig::load)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(API_KEY_PROP)
        .hasMessageContaining(FAKE_LLM_PROP);
  }

  @Test
  void invalidToggleFailsFast() {
    System.setProperty(FAKE_LLM_PROP, "treu");

    assertThatThrownBy(ExampleLlmConfig::load)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("treu")
        .hasMessageContaining(FAKE_LLM_PROP);
  }
}
