// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import com.agentforge4j.llm.StandardNeutralConfiguration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.ollama.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link OllamaLlmClientFactory} consumes. Ollama requires no credential, so no
 * API-key reference is supplied. Activates when {@code enabled} is {@code true}; connect/request timeout defaults are
 * {@link OllamaDefaults#CONNECT_TIMEOUT} / {@link OllamaDefaults#REQUEST_TIMEOUT} — the same constants
 * {@link OllamaNeutralConfiguration#fromNeutral} falls back to for a programmatically constructed neutral
 * configuration that omits {@code request.timeout}.
 */
public final class OllamaConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "ollama";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.isTrue("enabled");
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new StandardNeutralConfiguration(
        "ollama",
        raw.get("default-model").orElse(null),
        raw.getDuration("connect-timeout").orElse(OllamaDefaults.CONNECT_TIMEOUT),
        raw.get("url").orElse(null),
        null,
        NeutralOptions.create()
            .duration("request.timeout", raw.getDuration("request-timeout").orElse(OllamaDefaults.REQUEST_TIMEOUT))
            .toMap());
  }
}
