// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.mistral;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.starter.llmclient.GenericLlmProviderAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Generic-path activation for Mistral: gated on API key, {@code base-url} becomes the base URL. */
class MistralProviderActivationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void skipsWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void registersAndMapsBaseUrlWhenApiKeySet() {
    runner.withPropertyValues(
        "agentforge4j.llm.mistral.api-key=k",
        "agentforge4j.llm.mistral.default-model=mistral-large",
        "agentforge4j.llm.mistral.base-url=https://api.mistral.ai")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("mistral");
          assertThat(cfg.getDefaultModel()).isEqualTo("mistral-large");
          assertThat(cfg.getBaseUrl()).isEqualTo("https://api.mistral.ai");
          assertThat(cfg.getApiKeyReference()).isPresent();
        });
  }
}
