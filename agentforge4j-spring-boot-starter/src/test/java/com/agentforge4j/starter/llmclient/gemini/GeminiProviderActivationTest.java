// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.starter.llmclient.GenericLlmProviderAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Generic-path activation for Gemini: gated on API key, {@code base-url} becomes the base URL. */
class GeminiProviderActivationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void skipsWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void registersAndMapsBaseUrlWhenApiKeySet() {
    runner.withPropertyValues(
        "agentforge4j.llm.gemini.api-key=k",
        "agentforge4j.llm.gemini.default-model=gemini-1.5-pro",
        "agentforge4j.llm.gemini.base-url=https://generativelanguage.googleapis.com")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("gemini");
          assertThat(cfg.getDefaultModel()).isEqualTo("gemini-1.5-pro");
          assertThat(cfg.getBaseUrl()).isEqualTo("https://generativelanguage.googleapis.com");
          assertThat(cfg.getApiKeyReference()).isPresent();
        });
  }
}
