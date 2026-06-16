// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating OpenAI LLM clients.
 * <p>
 * Discovered via JPMS ServiceLoader to provide OpenAI-specific client instances.
 */
public final class OpenAiLlmClientFactory implements LlmClientFactory {

  /**
   * Returns the provider name for OpenAI.
   *
   * @return "openai"
   */
  @Override
  public String getProviderName() {
    return "openai";
  }

  /**
   * Creates an OpenAI LLM client with the given configuration.
   *
   * @param objectMapper the JSON mapper for response parsing
   * @param config       the configuration, must be an instance of {@link OpenAiConfiguration}
   * @return a new OpenAI LLM client
   * @throws IllegalArgumentException if the config is not an OpenAiConfiguration
   */
  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    Validate.notNull(config, "OpenAi configuration must not be null");
    if (!(config instanceof OpenAiConfiguration openAiConfig)) {
      throw new IllegalArgumentException(
          "OpenAiLlmClientFactory requires OpenAiConfiguration but got: %s".formatted(
              config.getClass().getName()));
    }
    return new OpenAiLlmClient(objectMapper, openAiConfig);
  }
}
