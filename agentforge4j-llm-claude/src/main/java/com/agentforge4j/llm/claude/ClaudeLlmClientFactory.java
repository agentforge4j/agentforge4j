package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ClaudeLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "claude";
  }

  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    Validate.notNull(config, "Claude configuration must not be null");
    if (!(config instanceof ClaudeConfiguration claudeConfig)) {
      throw new IllegalArgumentException(
          "ClaudeLlmClientFactory requires ClaudeConfiguration but got: %s".formatted(
              config.getClass().getName()));
    }
    return new ClaudeLlmClient(objectMapper, claudeConfig);
  }
}
