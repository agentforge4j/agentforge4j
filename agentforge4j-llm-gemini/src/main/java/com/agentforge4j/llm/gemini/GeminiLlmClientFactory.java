// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;

/**
 * Factory for creating Gemini LLM clients.
 */
public final class GeminiLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "gemini";
  }

  /**
   * Creates a Gemini client from a neutral {@link LlmClientFactoryContext}: resolves the credential reference and maps
   * the neutral configuration and provider options into the validated {@link GeminiConfiguration}.
   *
   * @param context the factory inputs
   *
   * @return a new Gemini LLM client
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    LlmSecret apiKey = context.requireApiKey();
    GeminiConfiguration config = GeminiNeutralConfiguration.fromNeutral(context.configuration(), apiKey);
    return new GeminiLlmClient(context.objectMapper(), config);
  }
}
