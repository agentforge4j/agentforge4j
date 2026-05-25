package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating Ollama {@link LlmClient} instances.
 * <p>
 * Requires an {@link OllamaConfiguration} instance; any other configuration type will raise an
 * {@link IllegalArgumentException}.
 */
public final class OllamaLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "ollama";
  }

  @Override
  public boolean requiresApiKey() {
    return false;
  }

  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    Validate.notNull(config, "Ollama configuration must not be null");
    if (!(config instanceof OllamaConfiguration ollamaConfig)) {
      throw new IllegalArgumentException(
          "OllamaLlmClientFactory requires OllamaConfiguration but got: %s".formatted(
              config.getClass().getName()));
    }
    return new OllamaLlmClient(objectMapper, ollamaConfig);
  }
}
