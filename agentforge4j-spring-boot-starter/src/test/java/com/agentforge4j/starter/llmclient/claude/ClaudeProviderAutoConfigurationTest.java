package com.agentforge4j.starter.llmclient.claude;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.claude.ClaudeConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClaudeProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ClaudeProviderAutoConfiguration.class));

  @Test
  void registersWhenApiKeySet() {
    runner.withPropertyValues("agentforge4j.llm.claude.api-key=test-key")
        .run(ctx -> assertThat(ctx).hasSingleBean(ClaudeConfiguration.class));
  }

  @Test
  void skipsWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(ClaudeConfiguration.class));
  }

  @Test
  void appliesConfiguredFieldsWhenApiKeySet() {
    runner.withPropertyValues(
        "agentforge4j.llm.claude.api-key=test-key",
        "agentforge4j.llm.claude.default-model=claude-3-haiku",
        "agentforge4j.llm.claude.api-version=2023-06-01",
        "agentforge4j.llm.claude.url=https://api.anthropic.test/v1/messages",
        "agentforge4j.llm.claude.max-token-size=4096")
        .run(ctx -> {
          ClaudeConfiguration cfg = ctx.getBean(ClaudeConfiguration.class);
          assertThat(cfg.getDefaultModel()).isEqualTo("claude-3-haiku");
          assertThat(cfg.getMaxTokenSize()).isEqualTo(4096);
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getRequestTimeout()).isEqualTo(Duration.ofMinutes(2));
          assertThat(cfg.getProviderName()).isEqualTo("claude");
        });
  }
}
