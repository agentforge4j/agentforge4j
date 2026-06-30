// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import java.time.Duration;
import java.util.Optional;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.gemini.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link GeminiLlmClientFactory} consumes. The API key is wrapped as a literal
 * credential reference; {@code base-url} becomes the base URL. Activates when an API key is present; connect/request
 * timeout defaults are 10s / 2m.
 */
public final class GeminiConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "gemini";
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
        raw.getDuration("request-timeout").orElse(Duration.ofMinutes(2)));
  }

  private record NeutralConfiguration(String defaultModel, Duration connectTimeout, String baseUrl,
                                      LlmSecretReference apiKeyReference, Duration requestTimeout)
      implements LlmClientConfiguration {

    @Override
    public String getProviderName() {
      return "gemini";
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
      return LlmProviderOptions.of("gemini", NeutralOptions.create()
          .duration("request.timeout", requestTimeout)
          .toMap());
    }
  }
}
