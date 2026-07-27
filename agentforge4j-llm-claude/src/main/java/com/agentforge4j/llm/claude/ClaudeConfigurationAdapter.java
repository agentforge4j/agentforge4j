// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import com.agentforge4j.llm.StandardNeutralConfiguration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.claude.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link ClaudeLlmClientFactory} consumes. The API key is wrapped as a literal
 * credential reference; {@code api-version} and {@code max-token-size} become the {@code api.version} and
 * {@code max.token.size} options. Activates when an API key is present; connect/request timeout defaults are
 * {@link ClaudeDefaults#CONNECT_TIMEOUT} / {@link ClaudeDefaults#REQUEST_TIMEOUT} — the same constants
 * {@link ClaudeNeutralConfiguration#fromNeutral} falls back to for a programmatically constructed neutral
 * configuration that omits {@code request.timeout}.
 */
public final class ClaudeConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "claude";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.get("api-key").isPresent();
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new StandardNeutralConfiguration(
        "claude",
        raw.get("default-model").orElse(null),
        raw.getDuration("connect-timeout").orElse(ClaudeDefaults.CONNECT_TIMEOUT),
        raw.get("url").orElse(null),
        LlmSecretReference.literal(raw.get("api-key").orElse(null)),
        NeutralOptions.create()
            .string("api.version", raw.get("api-version").orElse(null))
            .duration("request.timeout", raw.getDuration("request-timeout").orElse(ClaudeDefaults.REQUEST_TIMEOUT))
            .number("max.token.size", raw.getInt("max-token-size").orElse(null))
            .toMap());
  }
}
