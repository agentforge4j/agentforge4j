package com.agentforge4j.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating {@link LlmClient} instances.
 * <p>
 * Implementations are discovered via JPMS {@link java.util.ServiceLoader} to provide
 * providerName-specific clients. One factory exists per providerName (e.g., OpenAI, Ollama, Claude).
 * Factory implementations are typically defined in providerName-specific modules like
 * {@code agentforge4j-llm-openai}.
 */
public interface LlmClientFactory {

  /**
   * Returns the providerName name this factory creates clients for.
   *
   * @return providerName name such as {@code "openai"} or {@code "ollama"}
   */
  String getProviderName();

  /**
   * Creates a new LLM client configured with the provided settings.
   *
   * @param objectMapper the JSON mapper used for response parsing and serialization
   * @param config       the configuration for this providerName
   * @return a fully constructed LLM client ready to execute requests
   */
  LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config);
}
