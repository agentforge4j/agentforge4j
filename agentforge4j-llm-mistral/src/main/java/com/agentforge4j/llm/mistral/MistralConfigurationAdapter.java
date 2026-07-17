// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import com.agentforge4j.llm.StandardNeutralConfiguration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.mistral.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link MistralLlmClientFactory} consumes. The API key is wrapped as a literal
 * credential reference; {@code base-url} becomes the base URL. Activates when an API key is present; connect/request
 * timeout defaults are {@link MistralDefaults#CONNECT_TIMEOUT} / {@link MistralDefaults#REQUEST_TIMEOUT} — the same
 * constants {@link MistralNeutralConfiguration#fromNeutral} falls back to for a programmatically constructed neutral
 * configuration that omits {@code request.timeout}.
 */
public final class MistralConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "mistral";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.get("api-key").isPresent();
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new StandardNeutralConfiguration(
        "mistral",
        raw.get("default-model").orElse(null),
        raw.getDuration("connect-timeout").orElse(MistralDefaults.CONNECT_TIMEOUT),
        raw.get("base-url").orElse(null),
        LlmSecretReference.literal(raw.get("api-key").orElse(null)),
        NeutralOptions.create()
            .duration("request.timeout", raw.getDuration("request-timeout").orElse(MistralDefaults.REQUEST_TIMEOUT))
            .toMap());
  }
}
