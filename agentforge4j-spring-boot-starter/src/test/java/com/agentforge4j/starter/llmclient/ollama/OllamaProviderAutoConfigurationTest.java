// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.ollama.OllamaConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OllamaProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OllamaProviderAutoConfiguration.class));

  @Test
  void registersWhenEnabled() {
    runner.withPropertyValues(
            "agentforge4j.llm.ollama.enabled=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(OllamaConfiguration.class));
  }

  @Test
  void skipsWhenNotEnabled() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(OllamaConfiguration.class));
  }

  @Test
  void appliesTimeoutsWhenEnabledAndForwardsUnsetModelAndUrlFromProperties() {
    runner.withPropertyValues("agentforge4j.llm.ollama.enabled=true")
        .run(ctx -> {
          var cfg = ctx.getBean(OllamaConfiguration.class);
          assertThat(cfg.getDefaultModel()).isNull();
          assertThat(cfg.getUrl()).isNull();
          assertThat(cfg.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(cfg.getRequestTimeout()).isEqualTo(Duration.ofMinutes(5));
        });
  }
}
