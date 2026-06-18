// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
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
        .run(ctx -> assertThat(ctx).hasSingleBean(LlmClientConfiguration.class));
  }

  @Test
  void skipsWhenNotEnabled() {
    runner.withPropertyValues(
            "agentforge4j.llm.bedrock.region=us-east-1",
            "agentforge4j.llm.bedrock.model-id=anthropic.claude-v2",
            "agentforge4j.llm.bedrock.anthropic-version=bedrock-2023-05-31")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void forwardsAnthropicVersionFromConfiguration() {
    runner.withPropertyValues(
            "agentforge4j.llm.bedrock.enabled=true",
            "agentforge4j.llm.bedrock.region=us-east-1",
            "agentforge4j.llm.bedrock.model-id=anthropic.claude-v2",
            "agentforge4j.llm.bedrock.anthropic-version=bedrock-2023-05-31")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("bedrock");
          assertThat(cfg.getOptions().requireString("anthropic.version"))
              .isEqualTo("bedrock-2023-05-31");
          assertThat(cfg.getOptions().requireString("region")).isEqualTo("us-east-1");
          assertThat(cfg.getDefaultModel()).isEqualTo("anthropic.claude-v2");
          assertThat(cfg.getBaseUrl()).isNull();
          assertThat(cfg.getApiKeyReference()).isEmpty();
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getOptions().requireDuration("request.timeout"))
              .isEqualTo(Duration.ofMinutes(2));
          assertThat(cfg.getOptions().integer("max.tokens")).isEmpty();
          assertThat(cfg.getOptions().decimal("temperature")).isEmpty();
        });
  }
}
