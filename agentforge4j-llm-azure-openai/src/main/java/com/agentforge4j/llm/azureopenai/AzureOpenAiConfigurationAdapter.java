// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import java.time.Duration;
import java.util.Optional;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.azure-openai.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link AzureOpenAiLlmClientFactory} consumes. {@code deployment-name} feeds both
 * the default model and the {@code deployment} option; {@code endpoint} becomes the base URL; {@code api-version}
 * becomes the {@code api.version} option. Activates when an API key is present; connect/request timeout defaults are
 * 10s / 2m.
 */
public final class AzureOpenAiConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "azure-openai";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.get("api-key").isPresent();
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new NeutralConfiguration(
        raw.get("deployment-name").orElse(null),
        raw.getDuration("connect-timeout").orElse(Duration.ofSeconds(10)),
        raw.get("endpoint").orElse(null),
        LlmSecretReference.literal(raw.get("api-key").orElse(null)),
        raw.get("api-version").orElse(null),
        raw.getDuration("request-timeout").orElse(Duration.ofMinutes(2)));
  }

  private record NeutralConfiguration(String defaultModel, Duration connectTimeout, String baseUrl,
                                      LlmSecretReference apiKeyReference, String apiVersion,
                                      Duration requestTimeout) implements LlmClientConfiguration {

    @Override
    public String getProviderName() {
      return "azure-openai";
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
      return Optional.of(apiKeyReference);
    }

    @Override
    public LlmProviderOptions getOptions() {
      return LlmProviderOptions.of("azure-openai", NeutralOptions.create()
          .string("deployment", defaultModel)
          .string("api.version", apiVersion)
          .duration("request.timeout", requestTimeout)
          .toMap());
    }
  }
}
