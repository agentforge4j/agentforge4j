// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.NeutralOptions;
import com.agentforge4j.llm.RawProviderConfiguration;
import com.agentforge4j.llm.StandardNeutralConfiguration;

/**
 * Maps the provider configuration subtree ({@code agentforge4j.llm.gemini.*}) into the neutral
 * {@link LlmClientConfiguration} the {@link GeminiLlmClientFactory} consumes. The API key is wrapped as a literal
 * credential reference; {@code base-url} becomes the base URL; {@code max-output-tokens} becomes the
 * {@code max.output.tokens} option consumed by {@link GeminiNeutralConfiguration#fromNeutral}. Activates when an API
 * key is present; connect/request timeout defaults are {@link GeminiDefaults#CONNECT_TIMEOUT} /
 * {@link GeminiDefaults#REQUEST_TIMEOUT} — the same constants {@link GeminiNeutralConfiguration#fromNeutral} falls
 * back to for a programmatically constructed neutral configuration that omits {@code request.timeout}.
 */
public final class GeminiConfigurationAdapter implements LlmClientConfigurationAdapter {

  @Override
  public String providerId() {
    return "gemini";
  }

  @Override
  public boolean isConfigured(RawProviderConfiguration raw) {
    return raw.get("api-key").isPresent();
  }

  @Override
  public LlmClientConfiguration adapt(RawProviderConfiguration raw) {
    return new StandardNeutralConfiguration(
        "gemini",
        raw.get("default-model").orElse(null),
        raw.getDuration("connect-timeout").orElse(GeminiDefaults.CONNECT_TIMEOUT),
        raw.get("base-url").orElse(null),
        LlmSecretReference.literal(raw.get("api-key").orElse(null)),
        NeutralOptions.create()
            .duration("request.timeout", raw.getDuration("request-timeout").orElse(GeminiDefaults.REQUEST_TIMEOUT))
            .number("max.output.tokens", raw.getInt("max-output-tokens").orElse(null))
            .toMap());
  }
}
