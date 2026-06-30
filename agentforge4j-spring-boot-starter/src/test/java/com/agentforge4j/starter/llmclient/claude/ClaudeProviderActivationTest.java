// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.claude;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.starter.llmclient.GenericLlmProviderAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Generic-path activation for Claude: gated on API key, emits {@code api.version} and {@code max.token.size} options. */
class ClaudeProviderActivationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GenericLlmProviderAutoConfiguration.class));

  @Test
  void skipsWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LlmClientConfiguration.class));
  }

  @Test
  void registersAndMapsVersionAndTokenOptions() {
    runner.withPropertyValues(
        "agentforge4j.llm.claude.api-key=k",
        "agentforge4j.llm.claude.default-model=claude-3-5-sonnet",
        "agentforge4j.llm.claude.api-version=2023-06-01",
        "agentforge4j.llm.claude.max-token-size=4096")
        .run(ctx -> {
          LlmClientConfiguration cfg = ctx.getBean(LlmClientConfiguration.class);
          assertThat(cfg.getProviderName()).isEqualTo("claude");
          assertThat(cfg.getDefaultModel()).isEqualTo("claude-3-5-sonnet");
          assertThat(cfg.getApiKeyReference()).isPresent();
          assertThat(cfg.getOptions().requireString("api.version")).isEqualTo("2023-06-01");
          assertThat(cfg.getOptions().requireInteger("max.token.size")).isEqualTo(4096);
        });
  }
}
