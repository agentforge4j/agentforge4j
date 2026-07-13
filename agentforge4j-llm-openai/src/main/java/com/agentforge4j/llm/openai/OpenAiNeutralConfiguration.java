// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Internal {@link OpenAiConfiguration} built from a neutral {@link LlmClientConfiguration} plus a resolved credential.
 * The credential is held as an {@link LlmSecret}, so the record's generated {@code toString()} stays redacted.
 *
 * @param defaultModel   the default model id
 * @param connectTimeout the HTTP connect timeout
 * @param url            the OpenAI API URL
 * @param apiKey         the resolved credential
 * @param requestTimeout the request timeout
 */
record OpenAiNeutralConfiguration(
    String defaultModel,
    Duration connectTimeout,
    String url,
    LlmSecret apiKey,
    Duration requestTimeout) implements OpenAiConfiguration {

  OpenAiNeutralConfiguration {
    Validate.notNull(apiKey, "openai apiKey must not be null");
  }

  /**
   * Maps a neutral configuration and resolved credential into this provider's validated form.
   *
   * <p>An absent {@code request.timeout} option defaults to {@link OpenAiDefaults#REQUEST_TIMEOUT} — the same value
   * {@link OpenAiConfigurationAdapter} applies for the properties-configured path — so the two construction paths
   * cannot silently diverge on what "the" default is.
   *
   * @param neutral the neutral provider configuration
   * @param apiKey  the resolved credential
   *
   * @return the validated configuration
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  static OpenAiNeutralConfiguration fromNeutral(LlmClientConfiguration neutral, LlmSecret apiKey) {
    Validate.notNull(neutral, "neutral configuration must not be null");
    return new OpenAiNeutralConfiguration(
        neutral.getDefaultModel(),
        neutral.getConnectTimeout(),
        neutral.requireBaseUrl(),
        apiKey,
        neutral.getOptions().duration("request.timeout").orElse(OpenAiDefaults.REQUEST_TIMEOUT));
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
  public Duration getRequestTimeout() {
    return requestTimeout;
  }
}
