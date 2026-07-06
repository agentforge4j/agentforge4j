// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.api;

import com.agentforge4j.util.Validate;
import java.util.Locale;

/**
 * Capability tier an agent or step declares instead of a concrete, versioned model string. The runtime resolves a tier
 * to a provider-specific model via {@link ModelTierResolver} at invocation time, so a model release becomes a
 * configuration change rather than a bundle edit.
 *
 * <p>This is orchestration metadata; it is never part of the provider-facing
 * {@link LlmExecutionRequest}. A raw model pin always takes precedence over a tier.
 */
public enum ModelTier {

  /**
   * Cheapest, fastest tier — suitable for routine, low-risk steps.
   */
  LITE,

  /**
   * Balanced default tier — general-purpose capability and cost.
   */
  STANDARD,

  /**
   * Strongest tier — for high-risk, review, generation, or reasoning-heavy steps.
   */
  POWERFUL,

  /**
   * Highest named capability tier — sits above {@link #POWERFUL} in the ordered capability vocabulary. Purely a
   * capability label: the configured {@link ModelTierResolver} decides which model each tier maps to, and a resolver
   * may map this tier to the same model as another tier where no distinct higher-capability model is configured.
   */
  PREMIUM;

  /**
   * Parses a declared tier name into a {@link ModelTier}, trimming surrounding whitespace and upper-casing before
   * matching. This is the single canonical tier-name parse shared by every caller (invocation and cost-estimate paths)
   * so the accepted set of spellings can never drift between them. Callers decide how to surface a failure — the
   * runtime rethrows as a hard {@link ModelTierResolutionException}; the estimate path catches it and emits a marker
   * key.
   *
   * @param name the tier name; must not be blank. Matching is whitespace-tolerant and case-insensitive, but the
   *             trimmed, upper-cased value must equal a declared tier
   *
   * @return the matching tier; never {@code null}
   *
   * @throws IllegalArgumentException if {@code name} is blank or does not match a declared tier
   */
  public static ModelTier fromName(String name) {
    Validate.notBlank(name, "model tier name must not be blank");
    return ModelTier.valueOf(name.trim().toUpperCase(Locale.ROOT));
  }
}
