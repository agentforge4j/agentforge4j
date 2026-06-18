// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;

/**
 * Factory for creating Ollama {@link LlmClient} instances.
 * <p>
 * Requires an {@link OllamaConfiguration} instance; any other configuration type will raise an
 * {@link IllegalArgumentException}.
 */
public final class OllamaLlmClientFactory implements LlmClientFactory {

  @Override
  public String getProviderName() {
    return "ollama";
  }

  @Override
  public boolean requiresApiKey() {
    return false;
  }

  /**
   * Creates an Ollama client from a neutral {@link LlmClientFactoryContext}: maps the neutral configuration and
   * provider options into the validated {@link OllamaConfiguration} and constructs the client. Ollama requires no
   * credential.
   *
   * @param context the factory inputs
   *
   * @return a new Ollama LLM client
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    OllamaConfiguration config = OllamaNeutralConfiguration.fromNeutral(context.configuration());
    return new OllamaLlmClient(context.objectMapper(), config);
  }
}
