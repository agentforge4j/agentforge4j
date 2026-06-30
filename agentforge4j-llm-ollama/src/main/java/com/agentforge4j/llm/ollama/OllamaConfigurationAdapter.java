// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import java.time.Duration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.ollama.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link OllamaLlmClientFactory} consumes. Ollama requires no credential, so no
 * API-key reference is supplied. Activates when {@code enabled} is {@code true}; connect/request timeout defaults are
 * 10s / 5m.
 */
public final class OllamaConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "ollama";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.isTrue("enabled");
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new NeutralConfiguration(
        raw.get("default-model").orElse(null),
        raw.getDuration("connect-timeout").orElse(Duration.ofSeconds(10)),
        raw.get("url").orElse(null),
        raw.getDuration("request-timeout").orElse(Duration.ofMinutes(5)));
  }

  private record NeutralConfiguration(String defaultModel, Duration connectTimeout, String baseUrl,
                                      Duration requestTimeout) implements LlmClientConfiguration {

    @Override
    public String getProviderName() {
      return "ollama";
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
    public LlmProviderOptions getOptions() {
      return LlmProviderOptions.of("ollama", NeutralOptions.create()
          .duration("request.timeout", requestTimeout)
          .toMap());
    }
  }
}
