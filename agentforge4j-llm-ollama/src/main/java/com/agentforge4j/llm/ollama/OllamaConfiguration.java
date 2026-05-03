package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;

import java.time.Duration;

/**
 * Configuration contract for Ollama LLM clients.
 * <p>
 * Implementations provide the Ollama REST API endpoint URL and request timeout duration.
 * If not overridden, defaults to the standard Ollama listening port on localhost.
 */
public interface OllamaConfiguration extends LlmClientConfiguration {

  @Override
  default String getProviderName() {
    return "ollama";
  }

  /**
   * Returns the Ollama REST API endpoint URL. Defaults to {@code http://localhost:11434/api/chat}.
   *
   * @return the API endpoint URL; never null or blank
   */
  default String getUrl() {
    return "http://localhost:11434/api/chat";
  }

  /**
   * Returns the timeout duration for requests to the Ollama API endpoint.
   *
   * @return the timeout duration; never null
   */
  Duration getRequestTimeout();
}
