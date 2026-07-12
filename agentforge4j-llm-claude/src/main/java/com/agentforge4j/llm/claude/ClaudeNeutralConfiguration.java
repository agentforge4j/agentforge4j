// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Internal {@link ClaudeConfiguration} built from a neutral {@link LlmClientConfiguration} plus a resolved credential.
 * The credential is held as an {@link LlmSecret}, so the record's generated {@code toString()} stays redacted.
 *
 * @param defaultModel   the default model id
 * @param connectTimeout the HTTP connect timeout
 * @param url            the Claude API URL
 * @param apiKey         the resolved credential
 * @param apiVersion     the Claude API version
 * @param requestTimeout the request timeout
 * @param maxTokenSize   the maximum token size per request
 */
record ClaudeNeutralConfiguration(
    String defaultModel,
    Duration connectTimeout,
    String url,
    LlmSecret apiKey,
    String apiVersion,
    Duration requestTimeout,
    int maxTokenSize) implements ClaudeConfiguration {

  ClaudeNeutralConfiguration {
    Validate.notNull(apiKey, "claude apiKey must not be null");
  }

  /**
   * Maps a neutral configuration and resolved credential into this provider's validated form.
   *
   * <p>An absent {@code request.timeout} option defaults to {@link ClaudeDefaults#REQUEST_TIMEOUT} — the same value
   * {@link ClaudeConfigurationAdapter} applies for the properties-configured path — so the two construction paths
   * cannot silently diverge on what "the" default is.
   *
   * @param neutral the neutral provider configuration
   * @param apiKey  the resolved credential
   *
   * @return the validated configuration
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  static ClaudeNeutralConfiguration fromNeutral(LlmClientConfiguration neutral, LlmSecret apiKey) {
    Validate.notNull(neutral, "neutral configuration must not be null");
    LlmProviderOptions options = neutral.getOptions();
    return new ClaudeNeutralConfiguration(
        neutral.getDefaultModel(),
        neutral.getConnectTimeout(),
        neutral.requireBaseUrl(),
        apiKey,
        options.requireString("api.version"),
        options.duration("request.timeout").orElse(ClaudeDefaults.REQUEST_TIMEOUT),
        options.requireInteger("max.token.size"));
  }

  @Override
  public String getDefaultModel() {
    return defaultModel;
  }

  @Override
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public String getApiKey() {
    return apiKey.value();
  }

  @Override
  public String getApiVersion() {
    return apiVersion;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  @Override
  public int getMaxTokenSize() {
    return maxTokenSize;
  }
}
