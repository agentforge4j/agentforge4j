// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.vllm;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.starter.llmclient.GenericLlmProviderAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Generic-path activation for vLLM: gated on {@code url}, no API key, default model defaults to empty. */
class VllmProviderActivationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void skipsWhenUrlMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void skipsWhenUrlIsFalse() {
    // A present value of "false" counts as absent for a presence guard, matching default @ConditionalOnProperty.
    runner.withPropertyValues("agentforge4j.llm.vllm.url=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void registersAndDefaultsEmptyModelWhenUrlSet() {
    runner.withPropertyValues("agentforge4j.llm.vllm.url=http://localhost:8000/v1")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("vllm");
          assertThat(cfg.getBaseUrl()).isEqualTo("http://localhost:8000/v1");
          assertThat(cfg.getDefaultModel()).isEmpty();
          assertThat(cfg.getApiKeyReference()).isEmpty();
        });
  }
}
