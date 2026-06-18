// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;

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
   * Creates a Claude client from a neutral {@link LlmClientFactoryContext}: resolves the credential reference and maps
   * the neutral configuration and provider options into the validated {@link ClaudeConfiguration}.
   *
   * @param context the factory inputs
   *
   * @return a new Claude LLM client
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    LlmSecret apiKey = context.requireApiKey();
    ClaudeConfiguration config = ClaudeNeutralConfiguration.fromNeutral(context.configuration(), apiKey);
    return new ClaudeLlmClient(context.objectMapper(), config);
  }
}
