// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.starter.llmclient.GenericLlmProviderAutoConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Generic-path activation for Bedrock: gated on {@code enabled=true} (not an API key), and produces a configuration
 * with no API-key reference and no base URL (AWS credential chain), emitting region/version/limit options.
 */
class BedrockProviderActivationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void registersWhenEnabled() {
    runner.withPropertyValues("agentforge4j.llm.bedrock.enabled=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(LlmClientConfiguration.class));
  }

  /**
   * A non-true {@code enabled} value (yes/on/1 or otherwise malformed) neither activates the provider nor fails startup,
   * matching the former {@code @ConditionalOnProperty(havingValue = "true")} gate the starter restores.
   */
  @ParameterizedTest
  @ValueSource(strings = {"yes", "on", "1", "maybe"})
  void nonTrueEnabledNeitherActivatesNorFails(String value) {
    runner.withPropertyValues("agentforge4j.llm.bedrock.enabled=" + value)
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class);
        });
  }

  @Test
  void skipsWhenEnabledFlagAbsent() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void skipsWhenEnabledIsFalse() {
    runner.withPropertyValues("agentforge4j.llm.bedrock.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void mapsRegionAndOptionsWithNoApiKeyOrBaseUrl() {
    runner.withPropertyValues(
        "agentforge4j.llm.bedrock.enabled=true",
        "agentforge4j.llm.bedrock.region=us-east-1",
        "agentforge4j.llm.bedrock.model-id=anthropic.claude-3-sonnet",
        "agentforge4j.llm.bedrock.anthropic-version=bedrock-2023-05-31",
        "agentforge4j.llm.bedrock.max-tokens=1024",
        "agentforge4j.llm.bedrock.temperature=0.7")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("bedrock");
          assertThat(cfg.getDefaultModel()).isEqualTo("anthropic.claude-3-sonnet");
          assertThat(cfg.getApiKeyReference()).isEmpty();
          assertThat(cfg.getBaseUrl()).isNull();
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getOptions().requireDuration("request.timeout")).isEqualTo(Duration.ofMinutes(2));
          assertThat(cfg.getOptions().requireString("region")).isEqualTo("us-east-1");
          assertThat(cfg.getOptions().requireString("anthropic.version")).isEqualTo("bedrock-2023-05-31");
          assertThat(cfg.getOptions().requireInteger("max.tokens")).isEqualTo(1024);
          assertThat(cfg.getOptions().string("temperature")).contains("0.7");
        });
  }
}
