// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.openaicompat;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenAiCompatibleProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OpenAiCompatibleProviderAutoConfiguration.class));

  @Test
  void registersWhenApiKeySet() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai-compatible.api-key=secret",
        "agentforge4j.llm.openai-compatible.base-url=https://gateway.local",
        "agentforge4j.llm.openai-compatible.auth-header-name=Authorization",
        "agentforge4j.llm.openai-compatible.auth-header-prefix=Bearer ",
        "agentforge4j.llm.openai-compatible.responses-path=/v1/responses")
        .run(ctx -> assertThat(ctx).hasSingleBean(LlmClientConfiguration.class));
  }

  @Test
  void skipsWhenApiKeyMissing() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai-compatible.base-url=https://gateway.local",
        "agentforge4j.llm.openai-compatible.auth-header-name=Authorization",
        "agentforge4j.llm.openai-compatible.auth-header-prefix=Bearer ",
        "agentforge4j.llm.openai-compatible.responses-path=/v1/responses")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void exposesPropertiesFromConfiguration() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai-compatible.api-key=secret",
        "agentforge4j.llm.openai-compatible.base-url=https://gateway.local",
        "agentforge4j.llm.openai-compatible.auth-header-name=Authorization",
        "agentforge4j.llm.openai-compatible.auth-header-prefix=Bearer ",
        "agentforge4j.llm.openai-compatible.responses-path=/v1/responses",
        "agentforge4j.llm.openai-compatible.default-model=llama-3")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("openai-compatible");
          assertThat(cfg.getDefaultModel()).isEqualTo("llama-3");
          assertThat(cfg.getBaseUrl()).isEqualTo("https://gateway.local");
          assertThat(cfg.getApiKeyReference().orElseThrow().literalValue()).isEqualTo("secret");
          assertThat(cfg.getOptions().requireString("auth.header.name")).isEqualTo("Authorization");
          assertThat(cfg.getOptions().requireString("responses.path")).isEqualTo("/v1/responses");
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getOptions().requireDuration("request.timeout"))
              .isEqualTo(Duration.ofMinutes(2));
        });
  }
}
