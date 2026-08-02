// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import com.agentforge4j.llm.StandardNeutralConfiguration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.openai.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link OpenAiLlmClientFactory} consumes. The configured API key is wrapped as a
 * literal credential reference; the request timeout is emitted as the canonical dotted option {@code request.timeout}.
 * Activates when an API key is present; connect/request timeout defaults are {@link OpenAiDefaults#CONNECT_TIMEOUT} /
 * {@link OpenAiDefaults#REQUEST_TIMEOUT} — the same constants {@link OpenAiNeutralConfiguration#fromNeutral} falls
 * back to for a programmatically constructed neutral configuration that omits {@code request.timeout}.
 */
public final class OpenAiConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "openai";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.get("api-key").isPresent();
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new StandardNeutralConfiguration(
        "openai",
        raw.get("default-model").orElse(null),
        raw.getDuration("connect-timeout").orElse(OpenAiDefaults.CONNECT_TIMEOUT),
        raw.get("url").orElse(null),
        LlmSecretReference.literal(raw.get("api-key").orElse(null)),
        NeutralOptions.create()
            .duration("request.timeout", raw.getDuration("request-timeout").orElse(OpenAiDefaults.REQUEST_TIMEOUT))
            .toMap());
  }
}
