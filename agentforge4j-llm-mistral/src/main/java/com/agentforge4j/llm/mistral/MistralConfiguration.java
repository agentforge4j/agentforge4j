// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral;

import com.agentforge4j.llm.LlmClientConfiguration;

import java.time.Duration;

/**
 * Configuration contract for Mistral AI LLM clients.
 * <p>
 * Implementations provide the Mistral API base URL, API key, and request timeout duration.
 */
public interface MistralConfiguration extends LlmClientConfiguration {

  @Override
  default String getProviderName() {
    return "mistral";
  }

  /**
   * Returns the Mistral API base URL.
   *
   * @return the API base URL; never null or blank
   */
  String getBaseUrl();

  /**
   * Returns the Mistral API key for authentication.
   *
   * @return the API key; never null or blank
   */
  String getApiKey();

  /**
   * Returns the timeout duration for requests to the Mistral API endpoint.
   *
   * @return the timeout duration; never null
   */
  Duration getRequestTimeout();
}
