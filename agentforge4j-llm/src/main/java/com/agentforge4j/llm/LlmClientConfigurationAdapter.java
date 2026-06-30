// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

/**
 * Provider-owned mapping from a provider's {@code agentforge4j.llm.<providerId>.*} configuration subtree to a neutral
 * {@link LlmClientConfiguration}. It lives in the provider module — alongside the provider's {@link LlmClientFactory} —
 * so the provider, which owns its option-key vocabulary, also owns its property mapping. Discovered via
 * {@link java.util.ServiceLoader}, mirroring {@link LlmClientFactory}.
 *
 * <p>Implementations must remain framework-neutral.
 */
public interface LlmClientConfigurationAdapter {

  /**
   * @return the provider id this adapter configures; must match the namespace segment
   *     ({@code agentforge4j.llm.<providerId>}) and the matching {@link LlmClientFactory#getProviderName()}
   */
  String providerId();

  /**
   * Whether this provider is configured by the given subtree. The default is "any property present"; override where the
   * provider's activation condition is stricter (for example an API key is required) or different (for example an
   * {@code enabled} flag, or credentials that are not an API key).
   *
   * @param raw this provider's configuration subtree
   *
   * @return {@code true} when the provider should be activated
   */
  default boolean isConfigured(RawProviderConfiguration raw) {
    return !raw.isEmpty();
  }

  /**
   * Maps this provider's configuration subtree to the neutral configuration the provider's {@link LlmClientFactory}
   * consumes. Provider-specific validation stays in the factory / its neutral-configuration mapping; this method only
   * translates property keys to neutral fields and the provider's dotted option vocabulary.
   *
   * @param raw this provider's configuration subtree (only called when {@link #isConfigured(RawProviderConfiguration)})
   *
   * @return the neutral client configuration
   */
  LlmClientConfiguration adapt(RawProviderConfiguration raw);
}
