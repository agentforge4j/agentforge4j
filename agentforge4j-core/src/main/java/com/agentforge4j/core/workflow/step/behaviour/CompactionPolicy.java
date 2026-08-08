// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.util.Validate;

/**
 * Deterministic thresholds that decide whether a compaction step performs work or no-ops.
 *
 * @param minSourceUnits    estimated tokens below which compaction always no-ops; must not be negative
 * @param minDownstreamReuse static downstream reuse count below which compaction no-ops; must not be
 *                          negative. A value of {@code 0} compacts regardless of reuse
 */
public record CompactionPolicy(
    int minSourceUnits,
    int minDownstreamReuse
) {

  public CompactionPolicy {
    Validate.isNotNegative(minSourceUnits, "CompactionPolicy minSourceUnits must not be negative");
    Validate.isNotNegative(minDownstreamReuse,
        "CompactionPolicy minDownstreamReuse must not be negative");
  }
}
