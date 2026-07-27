// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;

/**
 * Factory for creating Mistral AI {@link LlmClient} instances.
 * <p>
 * Discovered via JPMS ServiceLoader to provide Mistral-specific client instances.
 */
public final class MistralLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "mistral";
  }

  /**
   * Creates a Mistral client from a neutral {@link LlmClientFactoryContext}: resolves the credential reference and maps
   * the neutral configuration and provider options into the validated {@link MistralConfiguration}.
   *
   * @param context the factory inputs
   *
   * @return a new Mistral LLM client
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    LlmSecret apiKey = context.requireApiKey();
    MistralConfiguration config = MistralNeutralConfiguration.fromNeutral(context.configuration(), apiKey);
    return new MistralLlmClient(context.objectMapper(), config);
  }
}
