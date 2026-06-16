// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for creating {@link LlmClient} instances for one provider.
 * <p>
 * Implementations are discovered via JPMS {@link java.util.ServiceLoader}. Each factory handles a
 * single provider id and lives in a provider module (for example {@code agentforge4j-llm-openai}).
 */
public interface LlmClientFactory {

  /**
   * Returns the provider id this factory creates clients for.
   *
   * @return non-blank provider id such as {@code "openai"} or {@code "ollama"}
   */
  String getProviderName();

  /**
   * Returns {@code true} if this provider requires an API key to function.
   * Providers that run locally without authentication (e.g. Ollama, vLLM)
   * should override this to return {@code false}.
   *
   * <p>Used by the bootstrap module to determine whether to include a provider
   * when no explicit API key is configured.
   *
   * @return {@code true} if an API key is required; {@code false} otherwise
   */
  default boolean requiresApiKey() {
    return true;
  }

  /**
   * Creates a new LLM client configured with the provided settings.
   *
   * @param objectMapper the JSON mapper used for response parsing and serialization
   * @param config       configuration for this factory's provider (must match {@link #getProviderName()})
   * @return a fully constructed LLM client ready to execute requests
   */
  LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config);
}
