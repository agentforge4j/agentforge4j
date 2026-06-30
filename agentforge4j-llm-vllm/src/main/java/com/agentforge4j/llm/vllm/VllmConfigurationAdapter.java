// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import java.time.Duration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.vllm.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link VllmLlmClientFactory} consumes. vLLM requires no credential, so no API-key
 * reference is supplied. Activates when a {@code url} is present; the default model defaults to empty and
 * connect/request timeout defaults are 10s / 5m.
 */
public final class VllmConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "vllm";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.get("url").isPresent();
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new NeutralConfiguration(
        raw.get("default-model").orElse(""),
        raw.getDuration("connect-timeout").orElse(Duration.ofSeconds(10)),
        raw.get("url").orElse(null),
        raw.getDuration("request-timeout").orElse(Duration.ofMinutes(5)));
  }

  private record NeutralConfiguration(String defaultModel, Duration connectTimeout, String baseUrl,
                                      Duration requestTimeout) implements LlmClientConfiguration {

    @Override
    public String getProviderName() {
      return "vllm";
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
      return LlmProviderOptions.of("vllm", NeutralOptions.create()
          .duration("request.timeout", requestTimeout)
          .toMap());
    }
  }
}
