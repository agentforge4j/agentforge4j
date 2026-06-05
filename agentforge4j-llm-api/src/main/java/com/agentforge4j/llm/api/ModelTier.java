package com.agentforge4j.llm.api;

/**
 * Capability tier an agent or step declares instead of a concrete, versioned model string. The
 * runtime resolves a tier to a provider-specific model via {@link ModelTierResolver} at invocation
 * time, so a model release becomes a configuration change rather than a bundle edit.
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
  POWERFUL
}
