// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import com.agentforge4j.llm.StandardNeutralConfiguration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.azure-openai.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link AzureOpenAiLlmClientFactory} consumes. {@code deployment-name} feeds both
 * the default model and the {@code deployment} option; {@code endpoint} becomes the base URL; {@code api-version}
 * becomes the {@code api.version} option. Activates when an API key is present; connect/request timeout defaults are
 * {@link AzureOpenAiDefaults#CONNECT_TIMEOUT} / {@link AzureOpenAiDefaults#REQUEST_TIMEOUT} — the same constants
 * {@link AzureOpenAiNeutralConfiguration#fromNeutral} falls back to for a programmatically constructed neutral
 * configuration that omits {@code request.timeout}.
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
    String deploymentName = raw.get("deployment-name").orElse(null);
    return new StandardNeutralConfiguration(
        "azure-openai",
        deploymentName,
        raw.getDuration("connect-timeout").orElse(AzureOpenAiDefaults.CONNECT_TIMEOUT),
        raw.get("endpoint").orElse(null),
        LlmSecretReference.literal(raw.get("api-key").orElse(null)),
        NeutralOptions.create()
            .string("deployment", deploymentName)
            .string("api.version", raw.get("api-version").orElse(null))
            .duration("request.timeout",
                raw.getDuration("request-timeout").orElse(AzureOpenAiDefaults.REQUEST_TIMEOUT))
            .toMap());
  }
}
