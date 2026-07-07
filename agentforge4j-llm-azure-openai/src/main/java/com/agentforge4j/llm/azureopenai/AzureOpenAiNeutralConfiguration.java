// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Internal {@link AzureOpenAiConfiguration} built from a neutral {@link LlmClientConfiguration} plus a resolved
 * credential. The credential is held as an {@link LlmSecret}, so the record's generated {@code toString()} stays
 * redacted.
 *
 * @param defaultModel   the default model id
 * @param connectTimeout the HTTP connect timeout
 * @param endpoint       the Azure OpenAI resource endpoint
 * @param apiKey         the resolved credential
 * @param deploymentName the deployment name
 * @param apiVersion     the Azure OpenAI API version
 * @param requestTimeout the request timeout
 */
record AzureOpenAiNeutralConfiguration(
    String defaultModel,
    Duration connectTimeout,
    String endpoint,
    LlmSecret apiKey,
    String deploymentName,
    String apiVersion,
    Duration requestTimeout) implements AzureOpenAiConfiguration {

  AzureOpenAiNeutralConfiguration {
    Validate.notNull(apiKey, "azure-openai apiKey must not be null");
  }

  /**
   * Maps a neutral configuration and resolved credential into this provider's validated form. The resource endpoint is
   * taken from the neutral base URL.
   *
   * <p>An absent {@code request.timeout} option defaults to {@link AzureOpenAiDefaults#REQUEST_TIMEOUT} — the same
   * value {@link AzureOpenAiConfigurationAdapter} applies for the properties-configured path — so the two
   * construction paths cannot silently diverge on what "the" default is.
   *
   * @param neutral the neutral provider configuration
   * @param apiKey  the resolved credential
   *
   * @return the validated configuration
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  static AzureOpenAiNeutralConfiguration fromNeutral(LlmClientConfiguration neutral,
      LlmSecret apiKey) {
    Validate.notNull(neutral, "neutral configuration must not be null");
    LlmProviderOptions options = neutral.getOptions();
    return new AzureOpenAiNeutralConfiguration(
        neutral.getDefaultModel(),
        neutral.getConnectTimeout(),
        neutral.requireBaseUrl(),
        apiKey,
        options.requireString("deployment"),
        options.requireString("api.version"),
        options.duration("request.timeout").orElse(AzureOpenAiDefaults.REQUEST_TIMEOUT));
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
  public String getEndpoint() {
    return endpoint;
  }

  @Override
  public String getApiKey() {
    return apiKey.value();
  }

  @Override
  public String getDeploymentName() {
    return deploymentName;
  }

  @Override
  public String getApiVersion() {
    return apiVersion;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }
}
