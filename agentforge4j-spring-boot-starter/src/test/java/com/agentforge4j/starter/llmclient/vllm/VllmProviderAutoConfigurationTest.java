// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.vllm;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VllmProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(VllmProviderAutoConfiguration.class));

  @Test
  void registersWhenUrlSet() {
    runner.withPropertyValues("agentforge4j.llm.vllm.url=http://127.0.0.1:8000/v1/chat/completions")
        .run(ctx -> assertThat(ctx).hasSingleBean(LlmClientConfiguration.class));
  }

  @Test
  void skipsWhenUrlMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void appliesDefaultsWhenUrlSet() {
    runner.withPropertyValues("agentforge4j.llm.vllm.url=http://127.0.0.1:8000/v1/chat/completions")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("vllm");
          assertThat(cfg.getDefaultModel()).isEmpty();
          assertThat(cfg.getApiKeyReference()).isEmpty();
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getOptions().requireDuration("request.timeout"))
              .isEqualTo(Duration.ofMinutes(5));
        });
  }
}
