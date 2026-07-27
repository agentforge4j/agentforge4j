// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import com.agentforge4j.llm.StandardNeutralConfiguration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.openai-compatible.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link OpenAiCompatibleLlmClientFactory} consumes. The API key is wrapped as a
 * literal credential reference; {@code base-url} becomes the base URL; the auth-header and responses-path settings
 * become the {@code auth.header.name} / {@code auth.header.prefix} / {@code responses.path} options. Activates when an
 * API key is present; connect/request timeout defaults are {@link OpenAiCompatibleDefaults#CONNECT_TIMEOUT} /
 * {@link OpenAiCompatibleDefaults#REQUEST_TIMEOUT} — the same constants
 * {@link OpenAiCompatibleNeutralConfiguration#fromNeutral} falls back to for a programmatically constructed neutral
 * configuration that omits {@code request.timeout}.
 */
public final class OpenAiCompatibleConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "openai-compatible";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.get("api-key").isPresent();
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new StandardNeutralConfiguration(
        "openai-compatible",
        raw.get("default-model").orElse(null),
        raw.getDuration("connect-timeout").orElse(OpenAiCompatibleDefaults.CONNECT_TIMEOUT),
        raw.get("base-url").orElse(null),
        LlmSecretReference.literal(raw.get("api-key").orElse(null)),
        NeutralOptions.create()
            .string("auth.header.name", raw.get("auth-header-name").orElse(null))
            .string("auth.header.prefix", raw.get("auth-header-prefix").orElse(null))
            .string("responses.path", raw.get("responses-path").orElse(null))
            .duration("request.timeout",
                raw.getDuration("request-timeout").orElse(OpenAiCompatibleDefaults.REQUEST_TIMEOUT))
            .toMap());
  }
}
