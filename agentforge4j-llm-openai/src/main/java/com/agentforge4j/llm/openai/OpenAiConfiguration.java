// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.LlmClientConfiguration;

import java.time.Duration;

/**
 * Configuration for OpenAI LLM client.
 * <p>
 * Provides the API key, request timeout, and other settings needed to connect to OpenAI's API.
 */
public interface OpenAiConfiguration extends LlmClientConfiguration {

  /**
   * Returns the OpenAI API key.
   *
   * @return the API key for authentication
   */
  String getApiKey();

  /**
   * Returns the request timeout for OpenAI API calls.
   *
   * @return the timeout duration
   */
  Duration getRequestTimeout();

  @Override
  default String getProviderName() {
    return "openai";
  }

  /**
   * Returns the OpenAI API URL (must be supplied by application configuration).
   *
   * @return the API endpoint URL
   */
  String getUrl();
}
