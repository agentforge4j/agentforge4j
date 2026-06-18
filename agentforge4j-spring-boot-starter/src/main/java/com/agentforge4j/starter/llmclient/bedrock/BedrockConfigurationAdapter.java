// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.bedrock;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.starter.llmclient.NeutralOptions;
import java.time.Duration;

/**
 * Adapts {@link BedrockLlmClientProperties} to the neutral {@link LlmClientConfiguration} SPI. Bedrock authenticates
 * through the AWS SDK credential chain rather than a static key, so no API-key reference or base URL is supplied;
 * provider-specific settings are emitted as canonical dotted options consumed by the provider's factory.
 */
record BedrockConfigurationAdapter(BedrockLlmClientProperties properties)
    implements LlmClientConfiguration {

  @Override
  public String getProviderName() {
    return "bedrock";
  }

  @Override
  public String getDefaultModel() {
    return properties.modelId();
  }

  @Override
  public Duration getConnectTimeout() {
    return properties.connectTimeout();
  }

  @Override
  public LlmProviderOptions getOptions() {
    return LlmProviderOptions.of("bedrock", NeutralOptions.create()
        .string("region", properties.region())
        .string("anthropic.version", properties.anthropicVersion())
        .duration("request.timeout", properties.requestTimeout())
        .number("max.tokens", properties.maxTokens())
        .number("temperature", properties.temperature())
        .toMap());
  }
}
