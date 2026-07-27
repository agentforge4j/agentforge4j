// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Standard {@link LlmClientConfiguration} shape shared by every provider's {@link LlmClientConfigurationAdapter}.
 * Across providers the only variation is which entries land in {@code options} (see {@link NeutralOptions}) and
 * whether an API-key reference exists at all — self-hosted / credential-less providers (for example Ollama, vLLM,
 * Bedrock) pass {@code null}. Every other component has the same shape everywhere, so adapters construct this record
 * instead of each hand-writing its own private {@link LlmClientConfiguration} implementation.
 *
 * @param providerId      the provider id this configuration is for (see {@link #getProviderName()}); must not be
 *                         blank
 * @param defaultModel    the default model id, or {@code null} when the provider has none
 * @param connectTimeout  the HTTP connect timeout; must not be {@code null}
 * @param baseUrl         the service base URL, or {@code null} for providers with no HTTP base URL (for example
 *                        Bedrock, reached through the AWS SDK rather than a base URL)
 * @param apiKeyReference the credential reference, or {@code null} for providers that need none
 * @param options         the canonical dotted provider-option map (see {@link NeutralOptions#toMap()}); must not be
 *                        {@code null} (empty when the provider has no extra options)
 */
public record StandardNeutralConfiguration(
    String providerId,
    String defaultModel,
    Duration connectTimeout,
    String baseUrl,
    LlmSecretReference apiKeyReference,
    Map<String, String> options) implements LlmClientConfiguration {

  /**
   * Validates the required components and defensively copies {@code options}.
   */
  public StandardNeutralConfiguration {
    Validate.notBlank(providerId, "providerId must not be blank");
    Validate.notNull(connectTimeout, "connectTimeout must not be null");
    Validate.notNull(options, "options must not be null");
    options = Map.copyOf(options);
  }

  @Override
  public String getProviderName() {
    return providerId;
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
  public Optional<LlmSecretReference> getApiKeyReference() {
    return Optional.ofNullable(apiKeyReference);
  }

  @Override
  public LlmProviderOptions getOptions() {
    return LlmProviderOptions.of(providerId, options);
  }
}
