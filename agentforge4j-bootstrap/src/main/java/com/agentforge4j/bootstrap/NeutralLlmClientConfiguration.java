// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.util.Validate;
import java.time.Duration;
import java.util.Optional;

/**
 * Neutral {@link LlmClientConfiguration} over a bootstrap {@link LlmProviderConfig}. Replaces the former
 * {@code ProviderConfigAdapter}, which dropped {@code apiKey}/{@code baseUrl} and all provider-specific settings; this
 * exposes them so provider factories can build their typed configuration from the neutral form.
 */
final class NeutralLlmClientConfiguration implements LlmClientConfiguration {

  private final LlmProviderConfig config;
  private final LlmProviderOptions options;

  NeutralLlmClientConfiguration(LlmProviderConfig config) {
    this.config = Validate.notNull(config, "config must not be null");
    this.options = LlmProviderOptions.of(config.provider(), config.options());
  }

  @Override
  public String getProviderName() {
    return config.provider();
  }

  @Override
  public String getDefaultModel() {
    return config.defaultModel();
  }

  @Override
  public Duration getConnectTimeout() {
    return config.connectTimeout();
  }

  @Override
  public String getBaseUrl() {
    return config.baseUrl();
  }

  @Override
  public Optional<LlmSecretReference> getApiKeyReference() {
    return Optional.ofNullable(config.apiKeyReference());
  }

  @Override
  public LlmProviderOptions getOptions() {
    return options;
  }
}
