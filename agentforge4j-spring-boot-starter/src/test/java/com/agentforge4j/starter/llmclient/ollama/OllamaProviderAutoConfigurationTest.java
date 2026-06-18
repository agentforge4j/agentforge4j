// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OllamaProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OllamaProviderAutoConfiguration.class));

  @Test
  void registersWhenEnabled() {
    runner.withPropertyValues(
            "agentforge4j.llm.ollama.enabled=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(LlmClientConfiguration.class));
  }

  @Test
  void skipsWhenNotEnabled() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void appliesTimeoutsWhenEnabledAndForwardsUnsetModelAndUrlFromProperties() {
    runner.withPropertyValues("agentforge4j.llm.ollama.enabled=true")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("ollama");
          assertThat(cfg.getDefaultModel()).isNull();
          assertThat(cfg.getBaseUrl()).isNull();
          assertThat(cfg.getApiKeyReference()).isEmpty();
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getOptions().requireDuration("request.timeout"))
              .isEqualTo(Duration.ofMinutes(5));
        });
  }
}
