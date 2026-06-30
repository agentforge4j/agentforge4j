// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.starter.llmclient.GenericLlmProviderAutoConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Generic-path activation for Ollama: gated on {@code enabled=true}, no API key, {@code url} becomes the base URL. */
class OllamaProviderActivationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void skipsWhenNotEnabled() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  /**
   * A non-true {@code enabled} value (yes/on/1, false, or otherwise malformed) neither activates the provider nor fails
   * startup, matching the former {@code @ConditionalOnProperty(havingValue = "true")} gate the starter restores.
   */
  @ParameterizedTest
  @ValueSource(strings = {"yes", "on", "1", "false", "maybe"})
  void nonTrueEnabledNeitherActivatesNorFails(String value) {
    runner.withPropertyValues("agentforge4j.llm.ollama.enabled=" + value)
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class);
        });
  }

  @Test
  void registersAndMapsWhenEnabled() {
    runner.withPropertyValues(
        "agentforge4j.llm.ollama.enabled=true",
        "agentforge4j.llm.ollama.default-model=llama3",
        "agentforge4j.llm.ollama.url=http://localhost:11434")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("ollama");
          assertThat(cfg.getDefaultModel()).isEqualTo("llama3");
          assertThat(cfg.getBaseUrl()).isEqualTo("http://localhost:11434");
          assertThat(cfg.getApiKeyReference()).isEmpty();
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getOptions().requireDuration("request.timeout")).isEqualTo(Duration.ofMinutes(5));
        });
  }
}
