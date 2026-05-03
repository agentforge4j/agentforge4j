package com.agentforge4j.llm.mistral;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating Mistral AI {@link LlmClient} instances.
 * <p>
 * Requires a {@link MistralConfiguration} instance; any other configuration type will raise
 * an {@link IllegalArgumentException}.
 */
public final class MistralLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "mistral";
  }

  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    if (!(config instanceof MistralConfiguration mistralConfig)) {
      throw new IllegalArgumentException(
        "MistralLlmClientFactory requires MistralConfiguration but got: %s"
          .formatted(config.getClass().getName()));
    }
    return new MistralLlmClient(objectMapper, mistralConfig);
  }
}
