package com.agentforge4j.llm;

/**
 * Resolves a provider id string to the configured {@link LlmClient}.
 * <p>
 * {@link DefaultLlmClientResolver} discovers factories via JPMS {@link java.util.ServiceLoader} and
 * builds clients from {@link LlmClientConfiguration} entries.
 */
public interface LlmClientResolver {

  /**
   * Returns the client for the given provider id (matched case-insensitively by typical implementations).
   *
   * @param provider provider id such as {@code "openai"} or {@code "ollama"}
   * @return client for that provider
   * @throws IllegalArgumentException if the provider is blank or not registered
   */
  LlmClient resolve(String provider);

  boolean isProviderAvailable(String provider);
}
