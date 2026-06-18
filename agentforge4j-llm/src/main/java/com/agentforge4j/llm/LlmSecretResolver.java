// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

/**
 * Resolves an {@link LlmSecretReference} to a live {@link LlmSecret} at the point of use.
 *
 * <p>This is the LLM layer's own credential seam, intentionally independent of any other secret
 * mechanism in the project. A literal reference resolves to its wrapped value; an indirect reference
 * ({@code scheme:key}) is looked up in the source the implementation backs.
 *
 * <p>Resolution is fail-fast: an indirect reference that cannot be resolved must throw (an
 * {@link LlmProviderConfigurationException}). Implementations must never log the resolved value or include it in any
 * exception message or other output — a failure message may name the {@code scheme:key} but never the value.
 */
public interface LlmSecretResolver {

  /**
   * Resolves a credential reference to its live value.
   *
   * @param reference the reference to resolve; must not be {@code null}
   *
   * @return the resolved secret; never {@code null}
   *
   * @throws LlmProviderConfigurationException if an indirect reference cannot be resolved
   */
  LlmSecret resolve(LlmSecretReference reference);
}
