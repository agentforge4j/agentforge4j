package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class OpenAiLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "openai";
  }

  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    if (!(config instanceof OpenAiConfiguration openAiConfig)) {
      throw new IllegalArgumentException(
          "OpenAiLlmClientFactory requires OpenAiConfiguration but got: %s".formatted(
              config.getClass().getName()));
    }
    return new OpenAiLlmClient(objectMapper, openAiConfig);
  }
}
