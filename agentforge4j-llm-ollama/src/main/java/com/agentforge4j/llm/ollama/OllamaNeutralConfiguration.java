// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Internal {@link OllamaConfiguration} built from a neutral {@link LlmClientConfiguration}. Ollama runs locally without
 * authentication, so no credential is involved.
 *
 * @param defaultModel   the default model id
 * @param connectTimeout the HTTP connect timeout
 * @param url            the Ollama REST API endpoint URL
 * @param requestTimeout the request timeout
 */
record OllamaNeutralConfiguration(
    String defaultModel,
    Duration connectTimeout,
    String url,
    Duration requestTimeout) implements OllamaConfiguration {

  /**
   * Maps a neutral configuration into this provider's validated form.
   *
   * @param neutral the neutral provider configuration
   *
   * @return the validated configuration
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  static OllamaNeutralConfiguration fromNeutral(LlmClientConfiguration neutral) {
    Validate.notNull(neutral, "neutral configuration must not be null");
    return new OllamaNeutralConfiguration(
        neutral.getDefaultModel(),
        neutral.getConnectTimeout(),
        neutral.requireBaseUrl(),
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
  public String getUrl() {
    return url;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }
}
