// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;

/**
 * Factory for creating Azure OpenAI LLM clients.
 * <p>
 * Discovered via JPMS ServiceLoader to provide Azure OpenAI-specific client instances.
 */
public final class AzureOpenAiLlmClientFactory implements LlmClientFactory {

  /**
   * Returns the provider name for Azure OpenAI.
   *
   * @return "azure-openai"
   */
  @Override
  public String getProviderName() {
    return "azure-openai";
  }

  /**
   * Creates an Azure OpenAI client from a neutral {@link LlmClientFactoryContext}: resolves the credential reference
   * and maps the neutral configuration and provider options into the validated {@link AzureOpenAiConfiguration}.
   *
   * @param context the factory inputs
   *
   * @return a new Azure OpenAI LLM client
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    LlmSecret apiKey = context.requireApiKey();
    AzureOpenAiConfiguration config = AzureOpenAiNeutralConfiguration.fromNeutral(context.configuration(), apiKey);
    return new AzureOpenAiLlmClient(context.objectMapper(), config);
  }
}
