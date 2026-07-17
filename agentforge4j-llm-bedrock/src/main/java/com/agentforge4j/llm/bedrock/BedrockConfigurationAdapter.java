// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import com.agentforge4j.llm.StandardNeutralConfiguration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.bedrock.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link BedrockLlmClientFactory} consumes. Bedrock authenticates through the AWS SDK
 * credential chain rather than a static key, so no API-key reference or base URL is supplied. Activates when
 * {@code enabled} is {@code true}; connect/request timeout defaults are {@link BedrockDefaults#CONNECT_TIMEOUT} /
 * {@link BedrockDefaults#REQUEST_TIMEOUT} — the same constants {@link BedrockNeutralConfiguration#fromNeutral} falls
 * back to for a programmatically constructed neutral configuration that omits {@code request.timeout}.
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
    return new StandardNeutralConfiguration(
        "bedrock",
        raw.get("model-id").orElse(null),
        raw.getDuration("connect-timeout").orElse(BedrockDefaults.CONNECT_TIMEOUT),
        null,
        null,
        NeutralOptions.create()
            .string("region", raw.get("region").orElse(null))
            .string("anthropic.version", raw.get("anthropic-version").orElse(null))
            .duration("request.timeout", raw.getDuration("request-timeout").orElse(BedrockDefaults.REQUEST_TIMEOUT))
            .number("max.tokens", raw.getInt("max-tokens").orElse(null))
            .number("temperature", raw.getDouble("temperature").orElse(null))
            .toMap());
  }
}
