package com.agentforge4j.starter.llmclient.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.gemini.GeminiConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GeminiProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(GeminiProviderAutoConfiguration.class));

  @Test
  void registersWhenApiKeySet() {
    runner.withPropertyValues("agentforge4j.llm.gemini.api-key=test-key")
        .run(ctx -> assertThat(ctx).hasSingleBean(GeminiConfiguration.class));
  }

  @Test
  void skipsWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(GeminiConfiguration.class));
  }

  @Test
  void appliesTimeoutDefaultsWhenOnlyApiKeySet() {
    runner.withPropertyValues(
        "agentforge4j.llm.gemini.api-key=test-key",
        "agentforge4j.llm.gemini.default-model=gemini-2.0-flash")
        .run(ctx -> {
          GeminiConfiguration cfg = ctx.getBean(GeminiConfiguration.class);
          assertThat(cfg.getDefaultModel()).isEqualTo("gemini-2.0-flash");
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getRequestTimeout()).isEqualTo(Duration.ofMinutes(2));
          assertThat(cfg.getProviderName()).isEqualTo("gemini");
        });
  }
}
