// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Internal {@link MistralConfiguration} built from a neutral {@link LlmClientConfiguration} plus a resolved credential.
 * The credential is held as an {@link LlmSecret}, so the record's generated {@code toString()} stays redacted.
 *
 * @param defaultModel   the default model id
 * @param connectTimeout the HTTP connect timeout
 * @param baseUrl        the Mistral API base URL
 * @param apiKey         the resolved credential
 * @param requestTimeout the request timeout
 */
record MistralNeutralConfiguration(
    String defaultModel,
    Duration connectTimeout,
    String baseUrl,
    LlmSecret apiKey,
    Duration requestTimeout) implements MistralConfiguration {

  MistralNeutralConfiguration {
    Validate.notNull(apiKey, "mistral apiKey must not be null");
  }

  /**
   * Maps a neutral configuration and resolved credential into this provider's validated form.
   *
   * @param neutral the neutral provider configuration
   * @param apiKey  the resolved credential
   *
   * @return the validated configuration
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  static MistralNeutralConfiguration fromNeutral(LlmClientConfiguration neutral, LlmSecret apiKey) {
    Validate.notNull(neutral, "neutral configuration must not be null");
    return new MistralNeutralConfiguration(
        neutral.getDefaultModel(),
        neutral.getConnectTimeout(),
        neutral.requireBaseUrl(),
        apiKey,
        neutral.getOptions().duration("request.timeout").orElse(Duration.ofSeconds(30)));
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
  public String getBaseUrl() {
    return baseUrl;
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
