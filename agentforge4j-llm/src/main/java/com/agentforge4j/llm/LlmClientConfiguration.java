package com.agentforge4j.llm;

import java.time.Duration;

/**
 * Configuration for an LLM providerName client.
 * <p>
 * Implementations provide the settings needed to construct and connect an {@link LlmClient}:
 * providerName name, default model, and connection timeout.
 */
public interface LlmClientConfiguration {

  /**
   * Returns the providerName name for this configuration.
   *
   * @return providerName name such as {@code "openai"} or {@code "ollama"}
   */
  String getProviderName();

  /**
   * Returns the default model to use if a request does not specify one.
   *
   * @return the default model identifier for this providerName
   */
  String getDefaultModel();

  /**
   * Returns the HTTP connection timeout for requests to this providerName.
   *
   * @return the connection timeout duration
   */
  Duration getConnectTimeout();
}
