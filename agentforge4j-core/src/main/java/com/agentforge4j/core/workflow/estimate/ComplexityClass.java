// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

/**
 * Deterministic complexity classification of a unit of work, ordered by increasing structure and
 * variance. The dominant axis is ceiling-derivability and structural predictability, not raw size:
 * a very large but bounded workload is {@link #HIGH_RISK}, never refused.
 *
 * <ul>
 *   <li>{@link #SIMPLE} — small, bounded, low variance.</li>
 *   <li>{@link #MODERATE} — moderate structure, still bounded and predictable.</li>
 *   <li>{@link #COMPLEX} — substantial bounded structure (loops, branching, nesting).</li>
 *   <li>{@link #HIGH_RISK} — a finite ceiling exists but uncertainty is high (agent-driven loops,
 *       large iteration ceilings, wide envelopes). Produces a wide range and low confidence; it
 *       never blocks use.</li>
 * </ul>
 */
public enum ComplexityClass {
  SIMPLE,
  MODERATE,
  COMPLEX,
  HIGH_RISK
}
