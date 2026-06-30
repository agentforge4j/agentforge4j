// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import java.time.Duration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.bedrock.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link BedrockLlmClientFactory} consumes. Bedrock authenticates through the AWS SDK
 * credential chain rather than a static key, so no API-key reference or base URL is supplied. Activates when
 * {@code enabled} is {@code true}; connect/request timeout defaults are 10s / 2m.
 */
public final class BedrockConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "bedrock";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.isTrue("enabled");
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new NeutralConfiguration(
        raw.get("model-id").orElse(null),
        raw.getDuration("connect-timeout").orElse(Duration.ofSeconds(10)),
        raw.get("region").orElse(null),
        raw.get("anthropic-version").orElse(null),
        raw.getInt("max-tokens").orElse(null),
        raw.getDouble("temperature").orElse(null),
        raw.getDuration("request-timeout").orElse(Duration.ofMinutes(2)));
  }

  private record NeutralConfiguration(String defaultModel, Duration connectTimeout, String region,
                                      String anthropicVersion, Integer maxTokens, Double temperature,
                                      Duration requestTimeout) implements LlmClientConfiguration {

    @Override
    public String getProviderName() {
      return "bedrock";
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
    public LlmProviderOptions getOptions() {
      return LlmProviderOptions.of("bedrock", NeutralOptions.create()
          .string("region", region)
          .string("anthropic.version", anthropicVersion)
          .duration("request.timeout", requestTimeout)
          .number("max.tokens", maxTokens)
          .number("temperature", temperature)
          .toMap());
    }
  }
}
