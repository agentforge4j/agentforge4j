package com.agentforge4j.starter;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmRetryPolicy;
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
 * <p>Provider-specific auto-configuration under {@code com.agentforge4j.starter.llmclient}
 * registers {@link LlmClientConfiguration} beans when optional LLM modules are on the classpath and
 * matching {@code agentforge4j.llm.*} properties apply; this class aggregates those beans and passes
 * them to {@link DefaultLlmClientResolver#discover(ObjectMapper, java.util.Collection)}.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
public class LlmAutoConfiguration {

  /**
   * Discovers providers from injected {@link LlmClientConfiguration} beans and exposes a single
   * resolver, optionally wrapped for retries.
   *
   * @throws IllegalArgumentException when {@code llmConfigurations} contains no configurations
   */
  @Bean
  @ConditionalOnMissingBean
  public LlmClientResolver llmClientResolver(ObjectMapper objectMapper,
      List<LlmClientConfiguration> llmConfigurations,
      @Value("${agentforge4j.llm.retry.max-attempts:1}") int retryMaxAttempts,
      @Value("${agentforge4j.llm.retry.base-backoff-ms:200}") long retryBaseBackoffMs,
      @Value("${agentforge4j.llm.retry.max-backoff-ms:10000}") long retryMaxBackoffMs,
      @Value("${agentforge4j.llm.retry.max-elapsed-ms:30000}") long retryMaxElapsedMs) {
    Validate.notEmpty(llmConfigurations,
        "No LlmClientConfiguration beans found — register at least one provider "
            + "(for example by importing an LLM provider starter or declaring a "
            + "configuration bean explicitly).");
    LlmClientResolver resolver = DefaultLlmClientResolver.discover(objectMapper, llmConfigurations);
    if (retryMaxAttempts <= 1) {
      return resolver;
    }
    // TODO: provider fallback (primary -> secondary provider/model) requires a selection strategy abstraction.
    LlmRetryPolicy retryPolicy =
        new LlmRetryPolicy(retryMaxAttempts, retryBaseBackoffMs, retryMaxBackoffMs, retryMaxElapsedMs);
    return new RetryingLlmClientResolver(resolver, retryPolicy);
  }
}
