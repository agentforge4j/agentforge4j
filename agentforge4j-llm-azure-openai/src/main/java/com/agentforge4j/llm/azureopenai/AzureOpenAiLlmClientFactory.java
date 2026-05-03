package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating Azure OpenAI LLM clients.
 * <p>
 * Discovered via JPMS ServiceLoader to provide Azure OpenAI-specific client instances.
 */
public final class AzureOpenAiLlmClientFactory implements LlmClientFactory {

  /**
   * Returns the provider name for Azure OpenAI.
   *
   * @return "azure-openai"
   */
  @Override
  public String getProviderName() {
    return "azure-openai";
  }

  /**
   * Creates an Azure OpenAI LLM client with the given configuration.
   *
   * @param objectMapper the JSON mapper for response parsing
   * @param config       the configuration, must be an instance of {@link AzureOpenAiConfiguration}
   * @return a new Azure OpenAI LLM client
   * @throws IllegalArgumentException if the config is not an AzureOpenAiConfiguration
   */
  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    Validate.notNull(config, "LLM client configuration must not be null");
    if (!(config instanceof AzureOpenAiConfiguration azureConfig)) {
      throw new IllegalArgumentException(
          "AzureOpenAiLlmClientFactory requires AzureOpenAiConfiguration but got: %s"
              .formatted(config.getClass().getName()));
    }
    return new AzureOpenAiLlmClient(objectMapper, azureConfig);
  }
}
