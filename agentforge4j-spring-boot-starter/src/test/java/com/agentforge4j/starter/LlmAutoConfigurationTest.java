package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.RetryingLlmClientResolver;
import com.agentforge4j.starter.llmclient.openai.OpenAiProviderAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class LlmAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(ObjectMapperTestConfiguration.class)
      .withConfiguration(AutoConfigurations.of(
          JacksonAutoConfiguration.class,
          OpenAiProviderAutoConfiguration.class,
          LlmAutoConfiguration.class));

  @Test
  void llmClientResolverBeanThrowsWhenNoProviderConfigurations() {
    assertThatThrownBy(() -> new LlmAutoConfiguration().llmClientResolver(
        new ObjectMapper(), List.of(), 1, 500))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No LlmClientConfiguration");
  }

  @Test
  void usesDefaultResolverWhenRetryMaxAttemptsIsOne() {
    runner.withPropertyValues(
            "agentforge4j.llm.openai.api-key=sk-test",
            "agentforge4j.llm.openai.default-model=gpt-4o-mini",
            "agentforge4j.llm.openai.url=https://api.openai.com/v1/responses")
        .run(ctx -> assertThat(ctx.getBean(LlmClientResolver.class))
            .isInstanceOf(DefaultLlmClientResolver.class));
  }

  @Test
  void wrapsWithRetryingResolverWhenRetryMaxAttemptsGreaterThanOne() {
    runner.withPropertyValues(
        "agentforge4j.llm.openai.api-key=sk-test",
        "agentforge4j.llm.openai.default-model=gpt-4o-mini",
        "agentforge4j.llm.openai.url=https://api.openai.com/v1/responses",
        "agentforge4j.llm.retry.max-attempts=3",
        "agentforge4j.llm.retry.backoff-ms=100")
        .run(ctx -> assertThat(ctx.getBean(LlmClientResolver.class))
            .isInstanceOf(RetryingLlmClientResolver.class));
  }

  @Configuration
  static class ObjectMapperTestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
