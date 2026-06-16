// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;

import java.time.Duration;

/**
 * Configuration contract for Ollama LLM clients.
 * <p>
 * Implementations provide the Ollama REST API endpoint URL and request timeout duration.
 */
public interface OllamaConfiguration extends LlmClientConfiguration {

  @Override
  default String getProviderName() {
    return "ollama";
  }

  /**
   * Returns the Ollama REST API endpoint URL (must be supplied by application configuration).
   *
   * @return the API endpoint URL; never null or blank for a working client
   */
  String getUrl();

  /**
   * Returns the timeout duration for requests to the Ollama API endpoint.
   *
   * @return the timeout duration; never null
   */
  Duration getRequestTimeout();
}
