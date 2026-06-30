// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import java.time.Duration;
import java.util.Optional;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.openai-compatible.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link OpenAiCompatibleLlmClientFactory} consumes. The API key is wrapped as a
 * literal credential reference; {@code base-url} becomes the base URL; the auth-header and responses-path settings
 * become the {@code auth.header.name} / {@code auth.header.prefix} / {@code responses.path} options. Activates when an
 * API key is present; connect/request timeout defaults are 10s / 2m.
 */
public final class OpenAiCompatibleConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "openai-compatible";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.get("api-key").isPresent();
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new NeutralConfiguration(
        raw.get("default-model").orElse(null),
        raw.getDuration("connect-timeout").orElse(Duration.ofSeconds(10)),
        raw.get("base-url").orElse(null),
        LlmSecretReference.literal(raw.get("api-key").orElse(null)),
        raw.get("auth-header-name").orElse(null),
        raw.get("auth-header-prefix").orElse(null),
        raw.get("responses-path").orElse(null),
        raw.getDuration("request-timeout").orElse(Duration.ofMinutes(2)));
  }

  private record NeutralConfiguration(String defaultModel, Duration connectTimeout, String baseUrl,
                                      LlmSecretReference apiKeyReference, String authHeaderName,
                                      String authHeaderPrefix, String responsesPath,
                                      Duration requestTimeout) implements LlmClientConfiguration {

    @Override
    public String getProviderName() {
      return "openai-compatible";
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
      return LlmProviderOptions.of("openai-compatible", NeutralOptions.create()
          .string("auth.header.name", authHeaderName)
          .string("auth.header.prefix", authHeaderPrefix)
          .string("responses.path", responsesPath)
          .duration("request.timeout", requestTimeout)
          .toMap());
    }
  }
}
