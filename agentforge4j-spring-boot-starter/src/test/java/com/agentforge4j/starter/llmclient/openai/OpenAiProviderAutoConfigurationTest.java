package com.agentforge4j.starter.llmclient.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.openai.OpenAiConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenAiProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OpenAiProviderAutoConfiguration.class));

  @Test
  void registersOpenAiConfigurationWhenApiKeySet() {
    runner.withPropertyValues("agentforge4j.llm.openai.api-key=sk-test")
        .run(ctx -> assertThat(ctx).hasSingleBean(OpenAiConfiguration.class));
  }

  @Test
  void skipsOpenAiConfigurationWhenApiKeyMissing() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(OpenAiConfiguration.class));
  }

  @Test
  void appliesConfiguredConnectTimeoutAndRecordDefaultsForUnsetOptionalFields() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai.api-key=sk-test",
        "agentforge4j.llm.openai.connect-timeout=15s")
        .run(ctx -> {
          var cfg = ctx.getBean(OpenAiConfiguration.class);
          assertThat(cfg.getDefaultModel()).isNull();
          assertThat(cfg.getUrl()).isNull();
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(15));
          assertThat(cfg.getRequestTimeout()).isEqualTo(Duration.ofMinutes(2));
        });
  }
}
