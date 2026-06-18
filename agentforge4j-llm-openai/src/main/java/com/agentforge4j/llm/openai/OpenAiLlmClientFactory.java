// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;

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
   * Creates an OpenAI client from a neutral {@link LlmClientFactoryContext}: resolves the credential reference and maps
   * the neutral configuration and provider options into the validated {@link OpenAiConfiguration}.
   *
   * @param context the factory inputs
   *
   * @return a new OpenAI LLM client
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    LlmSecret apiKey = context.requireApiKey();
    OpenAiConfiguration config = OpenAiNeutralConfiguration.fromNeutral(context.configuration(), apiKey);
    return new OpenAiLlmClient(context.objectMapper(), config);
  }
}
