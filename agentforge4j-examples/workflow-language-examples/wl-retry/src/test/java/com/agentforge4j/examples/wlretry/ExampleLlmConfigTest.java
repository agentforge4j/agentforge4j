// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlretry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ExampleLlmConfig}'s fake/real resolution and its strict parsing of the explicit
 * {@code agentforge4j.example.fake-llm} toggle. Each case drives resolution through the
 * highest-precedence source (a JVM system property), so the assertions are deterministic regardless of
 * the surrounding environment — except the two cases asserting the no-configuration default, which
 * additionally require {@code AGENTFORGE4J_EXAMPLE_LLM_API_KEY} and {@code AGENTFORGE4J_EXAMPLE_FAKE_LLM}
 * to be unset in the environment (true unless this module's own {@code .env.example} has been exported
 * into the shell); those two skip rather than fail if that ever isn't so.
 */
class ExampleLlmConfigTest {

  private static final String API_KEY_PROP = "agentforge4j.example.llm.api-key";
  private static final String FAKE_LLM_PROP = "agentforge4j.example.fake-llm";
  private static final String API_KEY_ENV = "AGENTFORGE4J_EXAMPLE_LLM_API_KEY";
  private static final String FAKE_LLM_ENV = "AGENTFORGE4J_EXAMPLE_FAKE_LLM";

  @AfterEach
  void clearProperties() {
    System.clearProperty(API_KEY_PROP);
    System.clearProperty(FAKE_LLM_PROP);
  }

  @Test
  void resolvesToFakeWhenNoToggleAndNoApiKey() {
    assumeEnvironmentUnset();
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
    assumeEnvironmentUnset();
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

  /**
   * Skips the calling test if the real-provider environment (as set up per {@code .env.example}) is
   * present — these two cases assert the fake default, which only holds with no API key configured
   * anywhere, including the environment.
   */
  private static void assumeEnvironmentUnset() {
    assumeTrue(System.getenv(API_KEY_ENV) == null,
        () -> "%s is set in the environment".formatted(API_KEY_ENV));
    assumeTrue(System.getenv(FAKE_LLM_ENV) == null,
        () -> "%s is set in the environment".formatted(FAKE_LLM_ENV));
  }
}
