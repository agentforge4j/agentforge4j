// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating vLLM LLM clients.
 * <p>
 * Discovered via JPMS ServiceLoader to provide vLLM-specific client instances.
 */
public final class VllmLlmClientFactory implements LlmClientFactory {

  /**
   * Returns the provider name for vLLM.
   *
   * @return "vllm"
   */
  @Override
  public String getProviderName() {
    return "vllm";
  }

  @Override
  public boolean requiresApiKey() {
    return false;
  }

  /**
   * Creates a vLLM LLM client with the given configuration.
   *
   * @param objectMapper the JSON mapper for response parsing
   * @param config       the configuration, must be an instance of {@link VllmConfiguration}
   * @return a new vLLM LLM client
   * @throws IllegalArgumentException if the config is not a VllmConfiguration
   */
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
