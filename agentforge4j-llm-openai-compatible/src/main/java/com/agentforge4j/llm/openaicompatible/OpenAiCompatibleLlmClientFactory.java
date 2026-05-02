package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class OpenAiCompatibleLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "openai-compatible";
  }

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
