// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

import com.agentforge4j.core.workflow.step.behaviour.CompactionMode;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.util.Validate;

/**
 * Provenance carried on a compact sibling state entry. The original source is never mutated or
 * replaced; the compact form records where it came from, how it was produced, and the estimated size
 * reduction, so a stale compact is detectable by fingerprint mismatch.
 *
 * @param sourceId             id of the source that was compacted; non-blank
 * @param sourceFingerprint    fingerprint of the canonicalized source at compaction time; non-blank
 * @param mode                 how the compact form was produced; never {@code null}
 * @param estimatedUnitsBefore estimated tokens of the source; must not be negative
 * @param estimatedUnitsAfter  estimated tokens of the compact form; must not be negative
 * @param producedByStepId     id of the compaction step that produced this sibling; non-blank
 * @param policySnapshot       the policy in effect when compaction ran; never {@code null}
 */
public record CompactSiblingMetadata(
    String sourceId,
    String sourceFingerprint,
    CompactionMode mode,
    int estimatedUnitsBefore,
    int estimatedUnitsAfter,
    String producedByStepId,
    CompactionPolicy policySnapshot
) {

  public CompactSiblingMetadata {
    Validate.notBlank(sourceId, "CompactSiblingMetadata sourceId must not be blank");
    Validate.notBlank(sourceFingerprint,
        "CompactSiblingMetadata sourceFingerprint must not be blank");
    Validate.notNull(mode, "CompactSiblingMetadata mode must not be null");
    Validate.isNotNegative(estimatedUnitsBefore,
        "CompactSiblingMetadata estimatedUnitsBefore must not be negative");
    Validate.isNotNegative(estimatedUnitsAfter,
        "CompactSiblingMetadata estimatedUnitsAfter must not be negative");
    Validate.notBlank(producedByStepId, "CompactSiblingMetadata producedByStepId must not be blank");
    Validate.notNull(policySnapshot, "CompactSiblingMetadata policySnapshot must not be null");
  }
}
