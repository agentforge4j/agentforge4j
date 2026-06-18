// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;

/**
 * Factory for creating OpenAI-compatible LLM clients.
 * <p>
 * Discovered via JPMS ServiceLoader to provide OpenAI-compatible client instances.
 */
public final class OpenAiCompatibleLlmClientFactory implements LlmClientFactory {

  /**
   * Returns the provider name for OpenAI-compatible.
   *
   * @return "openai-compatible"
   */
  @Override
  public String getProviderName() {
    return "openai-compatible";
  }

  /**
   * Creates an OpenAI-compatible client from a neutral {@link LlmClientFactoryContext}: resolves the credential
   * reference, maps the neutral configuration and provider options into the validated
   * {@link OpenAiCompatibleConfiguration}, and constructs the client.
   *
   * @param context the factory inputs
   *
   * @return a new OpenAI-compatible LLM client
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    LlmSecret apiKey = context.requireApiKey();
    OpenAiCompatibleConfiguration config =
        OpenAiCompatibleNeutralConfiguration.fromNeutral(context.configuration(), apiKey);
    return new OpenAiCompatibleLlmClient(context.objectMapper(), config);
  }
}
