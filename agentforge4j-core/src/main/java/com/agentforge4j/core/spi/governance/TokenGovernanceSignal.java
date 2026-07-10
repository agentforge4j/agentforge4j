// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.governance;

import com.agentforge4j.util.Validate;

/**
 * A deterministic, syntactic waste signal raised by the runtime's token-governance detector.
 * Advisory only — carried to {@link WasteSignalPolicy} and to the {@code TOKEN_GOVERNANCE_SIGNAL}
 * audit event; never blocks or alters execution.
 *
 * @param kind    the kind of signal; never {@code null}
 * @param stepId  the step the signal concerns; non-blank
 * @param agentId the agent the signal concerns, or {@code null} for a step-structural signal (for
 *                example {@link WasteSignalKind#OVERBROAD_CONTEXT}) with no single agent involved
 * @param detail  human-readable detail (relevant fingerprints, tier names, or loop coordinates,
 *                formatted per {@code kind}); non-blank
 */
public record TokenGovernanceSignal(
    WasteSignalKind kind,
    String stepId,
    String agentId,
    String detail
) {

  public TokenGovernanceSignal {
    Validate.notNull(kind, "TokenGovernanceSignal kind must not be null");
    Validate.notBlank(stepId, "TokenGovernanceSignal stepId must not be blank");
    Validate.notBlank(detail, "TokenGovernanceSignal detail must not be blank");
  }
}
