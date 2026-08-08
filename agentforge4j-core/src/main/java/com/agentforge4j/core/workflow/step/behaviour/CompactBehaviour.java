// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.util.Validate;

/**
 * Step that deterministically decides whether to compact a source into a compact sibling state entry,
 * no-ops when compaction is not worthwhile, and always records why. The original source is never
 * mutated or replaced.
 *
 * <p>Not a transition carrier: compaction advances deterministically like the other non-carrier
 * behaviours.
 *
 * @param source the source to compact; never {@code null}
 * @param mode   how the compact form is produced (deterministic extract or LLM summary); never
 *               {@code null}
 * @param policy the deterministic thresholds deciding whether compaction runs; never {@code null}
 */
public record CompactBehaviour(
    ContextSelector source,
    CompactionMode mode,
    CompactionPolicy policy
) implements StepBehaviour {

  public CompactBehaviour {
    Validate.notNull(source, "CompactBehaviour source must not be null");
    Validate.notNull(mode, "CompactBehaviour mode must not be null");
    Validate.notNull(policy, "CompactBehaviour policy must not be null");
  }
}
