package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.LlmClientConfiguration;

import java.time.Duration;

/**
 * Gemini REST client settings.
 */
public interface GeminiConfiguration extends LlmClientConfiguration {

  @Override
  default String getProviderName() {
    return "gemini";
  }

  /**
   * API base URL, for example {@code https://generativelanguage.googleapis.com}.
   */
  String getBaseUrl();

  /**
   * Returns the API key for authentication with the Gemini API.
   */
  String getApiKey();

  /**
   * Returns the timeout duration for HTTP requests to the Gemini API.
   */
  Duration getRequestTimeout();
}
