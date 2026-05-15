package com.agentforge4j.starter;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.RetryingLlmClientResolver;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Builds the {@link LlmClientResolver} from all {@link LlmClientConfiguration} beans present in the
 * context.
 *
 * <p>Provider starters (openai, ollama, …) contribute their own
 * {@link LlmClientConfiguration} beans; this auto-configuration aggregates them and hands the list
 * to {@link DefaultLlmClientResolver#discover(ObjectMapper, java.util.Collection)}.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
public class LlmAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LlmClientResolver llmClientResolver(ObjectMapper objectMapper,
      List<LlmClientConfiguration> llmConfigurations,
      @Value("${agentforge4j.llm.retry.max-attempts:1}") int retryMaxAttempts,
      @Value("${agentforge4j.llm.retry.backoff-ms:500}") long retryBackoffMs) {
    Validate.notEmpty(llmConfigurations,
        "No LlmClientConfiguration beans found — register at least one provider "
            + "(for example by importing an LLM provider starter or declaring a "
            + "configuration bean explicitly).");
    LlmClientResolver resolver = DefaultLlmClientResolver.discover(objectMapper, llmConfigurations);
    if (retryMaxAttempts <= 1) {
      return resolver;
    }
    // TODO: provider fallback (primary -> secondary provider/model) requires a selection strategy abstraction.
    return new RetryingLlmClientResolver(resolver, retryMaxAttempts, retryBackoffMs);
  }
}
