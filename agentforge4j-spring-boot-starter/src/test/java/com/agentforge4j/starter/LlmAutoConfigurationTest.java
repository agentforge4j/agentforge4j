package com.agentforge4j.starter;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.RetryingLlmClientResolver;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import com.agentforge4j.starter.llmclient.openai.OpenAiProviderAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        new ObjectMapper(), List.of(), 1,
        LlmRetryPolicy.defaults().baseBackoffMs(),
        LlmRetryPolicy.defaults().maxBackoffMs(),
        LlmRetryPolicy.defaults().maxElapsedMs()))
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
            "agentforge4j.llm.retry.base-backoff-ms=100",
            "agentforge4j.llm.retry.max-backoff-ms=5000",
            "agentforge4j.llm.retry.max-elapsed-ms=8000")
        .run(ctx -> assertThat(ctx.getBean(LlmClientResolver.class))
            .isInstanceOf(RetryingLlmClientResolver.class));
  }

  @Test
  void retryBinderUsesDefaultsMatchingLlmRetryPolicyDefaults_whenOnlyMaxAttemptsIsSetOverOne() {
    runner.withPropertyValues(
            "agentforge4j.llm.openai.api-key=sk-test",
            "agentforge4j.llm.openai.default-model=gpt-4o-mini",
            "agentforge4j.llm.openai.url=https://api.openai.com/v1/responses",
            "agentforge4j.llm.retry.max-attempts=3")
        .run(ctx -> {
          LlmClientResolver bean = ctx.getBean(LlmClientResolver.class);
          assertThat(bean).isInstanceOf(RetryingLlmClientResolver.class);
          LlmRetryPolicy embeddedPolicy = (LlmRetryPolicy)
              ReflectionTestUtils.getField(bean, "defaultPolicy");
          assertThat(embeddedPolicy).isEqualTo(LlmRetryPolicy.defaults());
        });
  }

  @Test
  void retryBinderMapsCustomRetryPropertiesOntoEmbeddedPolicy_inResolverBean() {
    runner.withPropertyValues(
            "agentforge4j.llm.openai.api-key=sk-test",
            "agentforge4j.llm.openai.default-model=gpt-4o-mini",
            "agentforge4j.llm.openai.url=https://api.openai.com/v1/responses",
            "agentforge4j.llm.retry.max-attempts=7",
            "agentforge4j.llm.retry.base-backoff-ms=11",
            "agentforge4j.llm.retry.max-backoff-ms=99",
            "agentforge4j.llm.retry.max-elapsed-ms=123")
        .run(ctx -> {
          LlmClientResolver resolver = ctx.getBean(LlmClientResolver.class);
          assertThat(resolver).isInstanceOf(RetryingLlmClientResolver.class);
          RetryingLlmClientResolver rr = (RetryingLlmClientResolver) resolver;
          LlmRetryPolicy embeddedPolicy = (LlmRetryPolicy)
              ReflectionTestUtils.getField(rr, "defaultPolicy");
          assertThat(embeddedPolicy).isEqualTo(new LlmRetryPolicy(7, 11, 99, 123));
        });
  }

  @Configuration
  static class ObjectMapperTestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
