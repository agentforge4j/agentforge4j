// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating Gemini LLM clients.
 */
public final class GeminiLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "gemini";
  }

  /**
   * Creates a new instance of {@link LlmClient} using the provided {@link ObjectMapper} and
   * {@link LlmClientConfiguration}.
   *
   * @param objectMapper the ObjectMapper to use
   * @param config       the configuration for the LLM client
   * @return a new instance of LlmClient
   * @throws IllegalArgumentException if the config is not an instance of GeminiConfiguration
   * @throws NullPointerException     if the config is null
   */
  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    Validate.notNull(config, "Gemini configuration must not be null");
    if (!(config instanceof GeminiConfiguration geminiConfig)) {
      throw new IllegalArgumentException(
          "GeminiLlmClientFactory requires GeminiConfiguration but got: %s"
              .formatted(config.getClass().getName()));
    }
    return new GeminiLlmClient(objectMapper, geminiConfig);
  }
}
