// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.azureopenai;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.starter.llmclient.NeutralOptions;
import java.time.Duration;
import java.util.Optional;

/**
 * Adapts {@link AzureOpenAiLlmClientProperties} to the neutral {@link LlmClientConfiguration} SPI. The Spring-resolved
 * API key is wrapped as a literal credential reference; provider-specific settings are emitted as canonical dotted
 * options consumed by the provider's factory.
 */
record AzureOpenAiConfigurationAdapter(AzureOpenAiLlmClientProperties properties)
    implements LlmClientConfiguration {

  @Override
  public String getProviderName() {
    return "azure-openai";
  }

  @Override
  public String getDefaultModel() {
    return properties.deploymentName();
  }

  @Override
  public Duration getConnectTimeout() {
    return properties.connectTimeout();
  }

  @Override
  public String getBaseUrl() {
    return properties.endpoint();
  }

  @Override
  public Optional<LlmSecretReference> getApiKeyReference() {
    return Optional.of(LlmSecretReference.literal(properties.apiKey()));
  }

  @Override
  public LlmProviderOptions getOptions() {
    return LlmProviderOptions.of("azure-openai", NeutralOptions.create()
        .string("deployment", properties.deploymentName())
        .string("api.version", properties.apiVersion())
        .duration("request.timeout", properties.requestTimeout())
        .toMap());
  }
}
