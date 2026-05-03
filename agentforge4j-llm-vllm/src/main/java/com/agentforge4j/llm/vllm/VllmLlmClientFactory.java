package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class VllmLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "vllm";
  }

  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    Validate.notNull(config, "Vllm configuration must not be null");
    if (!(config instanceof VllmConfiguration vllmConfig)) {
      throw new IllegalArgumentException(
          "VllmLlmClientFactory requires VllmConfiguration but got: %s".formatted(
              config.getClass().getName()));
    }
    return new VllmLlmClient(objectMapper, vllmConfig);
  }
}
