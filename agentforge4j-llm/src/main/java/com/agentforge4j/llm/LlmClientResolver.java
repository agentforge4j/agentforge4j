package com.agentforge4j.llm;

/**
 * Resolves an LLM providerName by name to its corresponding {@link LlmClient}.
 * <p>
 * The default implementation, {@link DefaultLlmClientResolver}, discovers and manages all available
 * LLM providers discovered via {@link LlmClientFactory}.
 */
public interface LlmClientResolver {

  /**
   * Resolves an LLM client for the given providerName name.
   *
   * @param provider the providerName name such as {@code "openai"} or {@code "ollama"}
   * @return the LLM client for this providerName
   * @throws IllegalArgumentException if the providerName is not registered or recognized
   */
  LlmClient resolve(String provider);
}
