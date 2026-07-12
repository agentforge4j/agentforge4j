// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Internal {@link OpenAiCompatibleConfiguration} built from a neutral {@link LlmClientConfiguration} plus a resolved
 * credential. The credential is held as an {@link LlmSecret}, so the record's generated {@code toString()} prints a
 * redacted form and never leaks the value.
 *
 * @param defaultModel     the default model id
 * @param connectTimeout   the HTTP connect timeout
 * @param baseUrl          the service base URL
 * @param apiKey           the resolved credential
 * @param requestTimeout   the request timeout
 * @param authHeaderName   the credential HTTP header name
 * @param authHeaderPrefix the prefix placed before the credential in the header value
 * @param responsesPath    the Responses API path appended to {@code baseUrl}
 */
record OpenAiCompatibleNeutralConfiguration(
    String defaultModel,
    Duration connectTimeout,
    String baseUrl,
    LlmSecret apiKey,
    Duration requestTimeout,
    String authHeaderName,
    String authHeaderPrefix,
    String responsesPath) implements OpenAiCompatibleConfiguration {

  OpenAiCompatibleNeutralConfiguration {
    Validate.notNull(apiKey, "openai-compatible apiKey must not be null");
  }

  /**
   * Maps a neutral configuration and resolved credential into this provider's validated form.
   *
   * <p>An absent {@code request.timeout} option defaults to {@link OpenAiCompatibleDefaults#REQUEST_TIMEOUT} — the
   * same value {@link OpenAiCompatibleConfigurationAdapter} applies for the properties-configured path — so the two
   * construction paths cannot silently diverge on what "the" default is.
   *
   * @param neutral the neutral provider configuration
   * @param apiKey  the resolved credential
   *
   * @return the validated configuration
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  static OpenAiCompatibleNeutralConfiguration fromNeutral(LlmClientConfiguration neutral,
      LlmSecret apiKey) {
    Validate.notNull(neutral, "neutral configuration must not be null");
    LlmProviderOptions options = neutral.getOptions();
    return new OpenAiCompatibleNeutralConfiguration(
        neutral.getDefaultModel(),
        neutral.getConnectTimeout(),
        neutral.requireBaseUrl(),
        apiKey,
        options.duration("request.timeout").orElse(OpenAiCompatibleDefaults.REQUEST_TIMEOUT),
        options.requireString("auth.header.name"),
        options.string("auth.header.prefix").orElse(""),
        options.requireString("responses.path"));
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

  @Override
  public String getAuthHeaderName() {
    return authHeaderName;
  }

  @Override
  public String getAuthHeaderPrefix() {
    return authHeaderPrefix;
  }

  @Override
  public String getResponsesPath() {
    return responsesPath;
  }
}
