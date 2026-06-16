// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating Claude LLM clients.
 * <p>
 * Discovered via JPMS ServiceLoader to provide Claude-specific client instances.
 */
public final class ClaudeLlmClientFactory implements LlmClientFactory {

  /**
   * Returns the provider name for Claude.
   *
   * @return "claude"
   */
  @Override
  public String getProviderName() {
    return "claude";
  }

  /**
   * Creates a Claude LLM client with the given configuration.
   *
   * @param objectMapper the JSON mapper for response parsing
   * @param config       the configuration, must be an instance of {@link ClaudeConfiguration}
   * @return a new Claude LLM client
   * @throws IllegalArgumentException if the config is not a ClaudeConfiguration
   */
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
