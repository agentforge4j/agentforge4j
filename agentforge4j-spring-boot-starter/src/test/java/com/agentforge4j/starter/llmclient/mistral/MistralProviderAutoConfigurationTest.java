// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.mistral;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MistralProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(MistralProviderAutoConfiguration.class));

  @Test
  void registersWhenApiKeySet() {
    runner.withPropertyValues("agentforge4j.llm.mistral.api-key=test-key")
        .run(ctx -> assertThat(ctx).hasSingleBean(LlmClientConfiguration.class));
  }

  @Test
  void skipsWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void appliesTimeoutDefaultsWhenOnlyApiKeySet() {
    runner.withPropertyValues(
        "agentforge4j.llm.mistral.api-key=test-key",
        "agentforge4j.llm.mistral.default-model=mistral-small")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getDefaultModel()).isEqualTo("mistral-small");
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getOptions().requireDuration("request.timeout"))
              .isEqualTo(Duration.ofMinutes(2));
          assertThat(cfg.getProviderName()).isEqualTo("mistral");
        });
  }
}
