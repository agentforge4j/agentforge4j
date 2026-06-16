// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.time.Duration;

/**
 * Configuration for Anthropic Claude LLM client.
 * <p>
 * Provides the API key, version, URL, timeout, and other settings needed to connect to Claude's API.
 */
public interface ClaudeConfiguration extends LlmClientConfiguration {

  /**
   * Returns the Claude API key.
   *
   * @return the API key for authentication
   */
  String getApiKey();

  /**
   * Returns the Claude API version.
   *
   * @return the API version string
   */
  String getApiVersion();

  /**
   * Returns the Claude API URL.
   *
   * @return the API endpoint URL
   */
  String getUrl();

  /**
   * Returns the request timeout for Claude API calls.
   *
   * @return the timeout duration
   */
  Duration getRequestTimeout();

  /**
   * Returns the maximum token size for Claude requests (must be supplied by application
   * configuration).
   */
  int getMaxTokenSize();

  @Override
  default String getProviderName() {
    return "claude";
  }
}
