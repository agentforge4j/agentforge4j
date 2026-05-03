package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating OpenAI-compatible LLM clients.
 * <p>
 * Discovered via JPMS ServiceLoader to provide OpenAI-compatible client instances.
 */
public final class OpenAiCompatibleLlmClientFactory implements LlmClientFactory {

  /**
   * Returns the provider name for OpenAI-compatible.
   *
   * @return "openai-compatible"
   */
  @Override
  public String getProviderName() {
    return "openai-compatible";
  }

  /**
   * Creates an OpenAI-compatible LLM client with the given configuration.
   *
   * @param objectMapper the JSON mapper for response parsing
   * @param config       the configuration, must be an instance of {@link OpenAiCompatibleConfiguration}
   * @return a new OpenAI-compatible LLM client
   * @throws IllegalArgumentException if the config is not an OpenAiCompatibleConfiguration
   */
  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    Validate.notNull(config, "OpenAiCompatible configuration must not be null");
    if (!(config instanceof OpenAiCompatibleConfiguration compatibleConfig)) {
      throw new IllegalArgumentException(
          "OpenAiCompatibleLlmClientFactory requires OpenAiCompatibleConfiguration but got: %s"
              .formatted(config.getClass().getName()));
    }
    return new OpenAiCompatibleLlmClient(objectMapper, compatibleConfig);
  }
}
