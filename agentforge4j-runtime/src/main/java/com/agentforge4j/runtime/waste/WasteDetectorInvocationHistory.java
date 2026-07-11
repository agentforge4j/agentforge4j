// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.waste;

import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.util.Validate;

/**
 * Persisted record of a step's most recent agent invocation, kept so {@link WasteDetector} can
 * compare the next invocation of the same step against it. Stored under
 * {@link com.agentforge4j.core.workflow.state.ReservedContextKeys#wasteDetectorInvocationHistoryKey(String)}.
 *
 * @param stepId                   the step this history belongs to; non-blank
 * @param agentId                  the agent invoked; non-blank
 * @param scopedContextFingerprint fingerprint of the rendered context for that invocation;
 *                                 non-blank
 * @param inputFingerprint         fingerprint of that invocation's full rendered input (context
 *                                 plus step prompt material); non-blank
 * @param resolvedTier             the tier resolved for that invocation, or {@code null} when the
 *                                 call used a raw model pin or the provider default (no tier
 *                                 involved)
 */
public record WasteDetectorInvocationHistory(
    String stepId,
    String agentId,
    String scopedContextFingerprint,
    String inputFingerprint,
    ModelTier resolvedTier
) {

  public WasteDetectorInvocationHistory {
    Validate.notBlank(stepId, "WasteDetectorInvocationHistory stepId must not be blank");
    Validate.notBlank(agentId, "WasteDetectorInvocationHistory agentId must not be blank");
    Validate.notBlank(scopedContextFingerprint,
        "WasteDetectorInvocationHistory scopedContextFingerprint must not be blank");
    Validate.notBlank(inputFingerprint,
        "WasteDetectorInvocationHistory inputFingerprint must not be blank");
  }
}
