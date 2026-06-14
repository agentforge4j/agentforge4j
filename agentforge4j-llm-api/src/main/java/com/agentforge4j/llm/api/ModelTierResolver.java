package com.agentforge4j.llm.api;

/**
 * Resolves a declared {@link ModelTier} to a concrete, versioned model string for a given
 * provider.
 *
 * <p>Implementations range from a static shipped default map to a context-aware, database-backed
 * resolver. Resolution is provider-scoped because the same tier maps to different model identifiers
 * across providers.
 */
public interface ModelTierResolver {

  /**
   * Resolves the concrete model string for the given provider and tier.
   *
   * @param provider the provider name (e.g. {@code "claude"}); must not be {@code null}
   * @param tier     the requested capability tier; must not be {@code null}
   *
   * @return the concrete model string, or {@code null} when no mapping exists for this
   * provider/tier combination
   */
  String resolve(String provider, ModelTier tier);
}
