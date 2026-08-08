// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.governance;

/**
 * Kind of deterministic, syntactic waste signal a {@link com.agentforge4j.core.spi.governance
 * governance} detector may raise. Advisory only: no signal ever blocks or alters execution.
 */
public enum WasteSignalKind {

  /**
   * Same agent, same scoped-context and input fingerprints, on a non-retry path.
   */
  DUPLICATE_INVOCATION,

  /**
   * A loop iteration's context fingerprint equals the previous iteration's.
   */
  UNCHANGED_LOOP_CONTEXT,

  /**
   * A loop iteration's normalized output fingerprint repeats one already seen in an earlier
   * iteration.
   */
  REPEATED_LOOP_OUTPUT,

  /**
   * A {@code COMPACT} step ran against an unchanged source fingerprint. In practice this is always
   * caught by the compaction step's own {@code UP_TO_DATE} skip rule (which no-ops and records why)
   * before a detector would ever observe it, so this kind exists for completeness of the vocabulary
   * rather than as a separately-raised signal.
   */
  REDUNDANT_COMPACTION,

  /**
   * A step's resolved capability tier increased relative to a prior invocation of the same step with
   * unchanged input and context fingerprints.
   */
  UNJUSTIFIED_TIER_ESCALATION,

  /**
   * A step declares no context selection in a workflow where at least one other step does.
   */
  OVERBROAD_CONTEXT
}
