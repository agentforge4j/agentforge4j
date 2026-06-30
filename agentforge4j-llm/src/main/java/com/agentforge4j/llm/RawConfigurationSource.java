// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

/**
 * Provider-scoped lookup of a single raw property value, backing {@link RawProviderConfiguration}. Given a property key
 * relative to a provider's subtree ({@code agentforge4j.llm.<providerId>.<key>}), it returns the raw string value
 * configured for that key, or {@code null} when no value is configured. The lookup is supplied by whichever component
 * wires AgentForge4j into its host configuration, so {@link RawProviderConfiguration} and the provider adapters that
 * consume it stay independent of any particular configuration mechanism.
 */
@FunctionalInterface
public interface RawConfigurationSource {

  /**
   * Resolves the value configured for {@code key} within this provider's subtree.
   *
   * @param key the property key relative to the provider subtree, in canonical kebab-case (for example
   *     {@code api-key}, {@code deployment-name}); never {@code null}
   *
   * @return the configured value when present (which may be blank), or {@code null} when no value is configured
   */
  String find(String key);
}
