package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.LlmClientConfiguration;

import java.time.Duration;

/**
 * Settings for an OpenAI Responses API–compatible endpoint.
 */
public interface OpenAiCompatibleConfiguration extends LlmClientConfiguration {

  @Override
  default String getProviderName() {
    return "openai-compatible";
  }

  /**
   * Service base URL (scheme + host, optional port), without trailing slash. The client resolves
   * the full request URI using {@link #getResponsesPath()}.
   */
  String getBaseUrl();

  /**
   * Returns the API key for authentication.
   *
   * @return the API key
   */
  String getApiKey();

  /**
   * Returns the request timeout for API calls.
   *
   * @return the timeout duration
   */
  Duration getRequestTimeout();

  /**
   * HTTP header name for credentials (must be supplied by application configuration).
   */
  String getAuthHeaderName();

  /**
   * Literal prefix placed before the API key in the header value. Use an empty string for
   * providers that expect the raw key as the entire value.
   */
  String getAuthHeaderPrefix();

  /**
   * Path appended to {@link #getBaseUrl()} for the Responses endpoint, starting with {@code /}
   * (must be supplied by application configuration).
   */
  String getResponsesPath();
}
