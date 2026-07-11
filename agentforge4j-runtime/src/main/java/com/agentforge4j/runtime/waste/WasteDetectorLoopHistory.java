// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.waste;

import com.agentforge4j.util.Validate;
import java.util.Set;

/**
 * Persisted record of a loop's iteration history, kept so {@link WasteDetector} can compare the
 * next iteration against what has already run. Stored under
 * {@link com.agentforge4j.core.workflow.state.ReservedContextKeys#wasteDetectorLoopHistoryKey(String)},
 * one entry per blueprint id, shared across every {@code LoopStrategy} implementation (all four
 * delegate iteration execution to {@code AbstractLoopStrategy}, which owns this history).
 *
 * @param blueprintId                the loop's enclosing blueprint id; non-blank
 * @param lastIterationContextFingerprint fingerprint of the previous iteration's non-reserved
 *                                    shared context, or {@code null} before the first iteration
 * @param seenOutputFingerprints     normalized-output fingerprints already observed in earlier
 *                                   iterations; never {@code null} (empty before any iteration
 *                                   has produced an output)
 */
public record WasteDetectorLoopHistory(
    String blueprintId,
    String lastIterationContextFingerprint,
    Set<String> seenOutputFingerprints
) {

  public WasteDetectorLoopHistory {
    Validate.notBlank(blueprintId, "WasteDetectorLoopHistory blueprintId must not be blank");
    Validate.notNull(seenOutputFingerprints,
        "WasteDetectorLoopHistory seenOutputFingerprints must not be null");
    seenOutputFingerprints = Set.copyOf(seenOutputFingerprints);
  }
}
