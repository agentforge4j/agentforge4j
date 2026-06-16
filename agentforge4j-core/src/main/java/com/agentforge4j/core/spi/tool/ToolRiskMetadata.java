// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

/**
 * Realised risk signal for a tool the runtime can invoke, carried by every {@link ToolDescriptor}.
 *
 * <p>This object is the single extension seam for tool risk metadata: further axes (for example
 * destructive, external-network, reads-secrets, writes-files) are added here as components without further churn to
 * {@link ToolDescriptor}. It currently carries one axis — mutation risk — matching the realised tool's own
 * declaration.
 *
 * <p><b>Signal contract</b> — binding on any {@link ToolPolicy} that consumes this metadata:
 * <ol>
 *   <li>It is a <em>signal</em>, not a decision: it informs a {@link ToolPolicy} decision and is
 *   never itself the decision.</li>
 *   <li>It may <em>raise</em>, never bypass: a policy may use the signal to <em>require</em> an
 *   approval, but must never use it to skip a policy-required approval. The effective requirement
 *   composes as {@code policyRequiresApproval || riskAwarePolicyRequiresApproval} — risk can only
 *   add.</li>
 *   <li>{@code mutating == false} is advisory: it may not reduce any policy-required approval.</li>
 *   <li>{@link ToolPolicy} is authoritative: it always overrides upward; a provider-supplied value
 *   never overrides policy downward. Provider- and server-declared metadata is untrusted input, the
 *   same posture as LLM output.</li>
 *   <li>Absent or unknown metadata defaults to the highest safe risk ({@link #conservative()}).</li>
 * </ol>
 *
 * @param mutating whether invoking the tool may mutate remote state (a hint to gate it with an approval policy)
 */
public record ToolRiskMetadata(boolean mutating) {

  /**
   * @return the highest-safe-risk instance ({@code mutating = true}), used wherever no trustworthy signal exists
   */
  public static ToolRiskMetadata conservative() {
    return new ToolRiskMetadata(true);
  }
}
