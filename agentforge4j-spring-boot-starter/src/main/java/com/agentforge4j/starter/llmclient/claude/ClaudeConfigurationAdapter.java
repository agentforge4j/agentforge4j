// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.claude;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.starter.llmclient.NeutralOptions;
import java.time.Duration;
import java.util.Optional;

/**
 * Adapts {@link ClaudeLlmClientProperties} to the neutral {@link LlmClientConfiguration} SPI. The Spring-resolved API
 * key is wrapped as a literal credential reference; provider-specific settings are emitted as canonical dotted options
 * consumed by the provider's factory.
 */
record ClaudeConfigurationAdapter(ClaudeLlmClientProperties properties)
    implements LlmClientConfiguration {

  @Override
  public String getProviderName() {
    return "claude";
  }

  @Override
  public String getDefaultModel() {
    return properties.defaultModel();
  }

  @Override
  public Duration getConnectTimeout() {
    return properties.connectTimeout();
  }

  @Override
  public String getBaseUrl() {
    return properties.url();
  }

  @Override
  public Optional<LlmSecretReference> getApiKeyReference() {
    return Optional.of(LlmSecretReference.literal(properties.apiKey()));
  }

  @Override
  public LlmProviderOptions getOptions() {
    return LlmProviderOptions.of("claude", NeutralOptions.create()
        .string("api.version", properties.apiVersion())
        .duration("request.timeout", properties.requestTimeout())
        .number("max.token.size", properties.maxTokenSize())
        .toMap());
  }
}
