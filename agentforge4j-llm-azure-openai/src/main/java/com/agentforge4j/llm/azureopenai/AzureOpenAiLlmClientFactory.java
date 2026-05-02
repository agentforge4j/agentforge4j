package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class AzureOpenAiLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "azure-openai";
  }

  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    if (!(config instanceof AzureOpenAiConfiguration azureConfig)) {
      throw new IllegalArgumentException(
          "AzureOpenAiLlmClientFactory requires AzureOpenAiConfiguration but got: %s"
              .formatted(config.getClass().getName()));
    }
    return new AzureOpenAiLlmClient(objectMapper, azureConfig);
  }
}
