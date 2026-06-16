// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.bedrock.BedrockConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BedrockProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(BedrockProviderAutoConfiguration.class));

  @Test
  void registersWhenEnabledWithRegionAndModelId() {
    runner.withPropertyValues(
            "agentforge4j.llm.bedrock.enabled=true",
            "agentforge4j.llm.bedrock.region=us-east-1",
            "agentforge4j.llm.bedrock.model-id=anthropic.claude-v2",
            "agentforge4j.llm.bedrock.anthropic-version=bedrock-2023-05-31")
        .run(ctx -> assertThat(ctx).hasSingleBean(BedrockConfiguration.class));
  }

  @Test
  void skipsWhenNotEnabled() {
    runner.withPropertyValues(
            "agentforge4j.llm.bedrock.region=us-east-1",
            "agentforge4j.llm.bedrock.model-id=anthropic.claude-v2",
            "agentforge4j.llm.bedrock.anthropic-version=bedrock-2023-05-31")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(BedrockConfiguration.class));
  }

  @Test
  void forwardsAnthropicVersionFromConfiguration() {
    runner.withPropertyValues(
            "agentforge4j.llm.bedrock.enabled=true",
            "agentforge4j.llm.bedrock.region=us-east-1",
            "agentforge4j.llm.bedrock.model-id=anthropic.claude-v2",
            "agentforge4j.llm.bedrock.anthropic-version=bedrock-2023-05-31")
        .run(ctx -> {
          var cfg = ctx.getBean(BedrockConfiguration.class);
          assertThat(cfg.getAnthropicVersion()).isEqualTo("bedrock-2023-05-31");
          assertThat(cfg.getDefaultModel()).isEqualTo("anthropic.claude-v2");
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getRequestTimeout()).isEqualTo(Duration.ofMinutes(2));
          assertThat(cfg.getMaxTokens()).isNull();
          assertThat(cfg.getTemperature()).isNull();
        });
  }
}
