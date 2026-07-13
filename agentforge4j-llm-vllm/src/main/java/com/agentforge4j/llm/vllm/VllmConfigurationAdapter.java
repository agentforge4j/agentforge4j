// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import com.agentforge4j.llm.StandardNeutralConfiguration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.vllm.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link VllmLlmClientFactory} consumes. vLLM requires no credential, so no API-key
 * reference is supplied. Activates when a {@code url} is present; the default model defaults to empty and
 * connect/request timeout defaults are {@link VllmDefaults#CONNECT_TIMEOUT} / {@link VllmDefaults#REQUEST_TIMEOUT} —
 * the same constants {@link VllmNeutralConfiguration#fromNeutral} falls back to for a programmatically constructed
 * neutral configuration that omits {@code request.timeout}.
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
    return new StandardNeutralConfiguration(
        "vllm",
        raw.get("default-model").orElse(""),
        raw.getDuration("connect-timeout").orElse(VllmDefaults.CONNECT_TIMEOUT),
        raw.get("url").orElse(null),
        null,
        NeutralOptions.create()
            .duration("request.timeout", raw.getDuration("request-timeout").orElse(VllmDefaults.REQUEST_TIMEOUT))
            .toMap());
  }
}
