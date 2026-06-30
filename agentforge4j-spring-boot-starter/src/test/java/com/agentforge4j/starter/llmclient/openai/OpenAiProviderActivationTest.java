// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.starter.llmclient.GenericLlmProviderAutoConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves the generic provider path activates the OpenAI provider end-to-end: the
 * {@code OpenAiConfigurationAdapter} (discovered via ServiceLoader from {@code agentforge4j-llm-openai}) is bound from
 * {@code agentforge4j.llm.openai.*} and registered as a neutral {@link LlmClientConfiguration} bean by
 * {@link GenericLlmProviderAutoConfiguration}. Only OpenAI is migrated to the generic path in this phase, so it is the
 * single configured provider here.
 */
class OpenAiProviderActivationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void registersOpenAiConfigurationWhenApiKeySet() {
    runner.withPropertyValues("agentforge4j.llm.openai.api-key=sk-test")
        .run(ctx -> assertThat(ctx).hasSingleBean(LlmClientConfiguration.class));
  }

  @Test
  void skipsOpenAiConfigurationWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void skipsOpenAiConfigurationWhenApiKeyIsFalse() {
    // A present value of "false" counts as absent for a presence guard, matching default @ConditionalOnProperty.
    runner.withPropertyValues("agentforge4j.llm.openai.api-key=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void appliesConfiguredConnectTimeoutAndRecordDefaultsForUnsetOptionalFields() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai.api-key=sk-test",
        "agentforge4j.llm.openai.connect-timeout=15s")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("openai");
          assertThat(cfg.getDefaultModel()).isNull();
          assertThat(cfg.getBaseUrl()).isNull();
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(15));
          assertThat(cfg.getOptions().requireDuration("request.timeout"))
              .isEqualTo(Duration.ofMinutes(2));
        });
  }

  @Test
  void mapsAllNeutralFieldsFromTheBoundSubtree() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai.api-key=sk-test",
        "agentforge4j.llm.openai.default-model=gpt-4o-mini",
        "agentforge4j.llm.openai.url=https://api.openai.com/v1/responses",
        "agentforge4j.llm.openai.request-timeout=PT45S")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("openai");
          assertThat(cfg.getDefaultModel()).isEqualTo("gpt-4o-mini");
          assertThat(cfg.getBaseUrl()).isEqualTo("https://api.openai.com/v1/responses");
          assertThat(cfg.getApiKeyReference()).isPresent();
          assertThat(cfg.getOptions().requireDuration("request.timeout"))
              .isEqualTo(Duration.ofSeconds(45));
        });
  }
}
