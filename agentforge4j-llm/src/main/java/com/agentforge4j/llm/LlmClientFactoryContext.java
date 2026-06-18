// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * Inputs passed to {@link LlmClientFactory#create(LlmClientFactoryContext)}: the JSON mapper, the neutral provider
 * {@link LlmClientConfiguration}, and the {@link LlmSecretResolver} a provider uses to resolve its credential
 * reference. A context (rather than a widening parameter list) lets the SPI absorb future inputs without further
 * {@code create} churn.
 *
 * @param objectMapper   the JSON mapper used for response parsing and serialization; never {@code null}
 * @param configuration  the neutral provider configuration; never {@code null}
 * @param secretResolver the resolver for the provider's credential reference; never {@code null}
 */
public record LlmClientFactoryContext(
    ObjectMapper objectMapper,
    LlmClientConfiguration configuration,
    LlmSecretResolver secretResolver) {

  public LlmClientFactoryContext {
    Validate.notNull(objectMapper, "objectMapper must not be null");
    Validate.notNull(configuration, "configuration must not be null");
    Validate.notNull(secretResolver, "secretResolver must not be null");
  }

  /**
   * Resolves the configuration's credential reference into a live {@link LlmSecret}. Use this from a provider that
   * requires an API key; the resolved value is held as an {@code LlmSecret}, never a raw {@code String}.
   *
   * @return the resolved credential
   *
   * @throws LlmProviderConfigurationException if no credential reference is configured (secret-safe message naming the
   *                                           provider)
   */
  public LlmSecret requireApiKey() {
    LlmSecretReference reference = configuration.getApiKeyReference()
        .orElseThrow(() -> new LlmProviderConfigurationException(
            "Provider '%s' requires an API key but none is configured"
                .formatted(configuration.getProviderName())));
    return secretResolver.resolve(reference);
  }

  /**
   * Resolves the configuration's credential reference if one is configured.
   *
   * @return the resolved credential, or empty when none is configured
   */
  public Optional<LlmSecret> apiKey() {
    return configuration.getApiKeyReference().map(secretResolver::resolve);
  }
}
