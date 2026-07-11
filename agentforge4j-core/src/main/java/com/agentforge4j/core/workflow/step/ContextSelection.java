// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Optional declaration on a {@link StepDefinition} of the context a step is scoped to, and the
 * additional context it <em>may</em> request at run time. A step with no {@link ContextSelection}
 * keeps the full-context behaviour.
 *
 * <p><strong>{@code selectors} is not yet enforced (0.1.0 status).</strong> It is validated at load
 * time against the enclosing workflow's declared sources, but the runtime does not yet assemble a
 * step's rendered agent context from it — an agent's actual input still flows entirely through its
 * own {@code ContextMapping}, unaffected by whatever {@code selectors} declares. Declaring
 * {@code selectors} today does not scope or restrict what the agent receives, and it is deliberately
 * excluded from COMPACT's compaction-reuse counting for the same reason (counting an unenforced
 * declaration would trigger real compaction work for no actual downstream effect); treat it as
 * reserved/tracked, not functional. This is an open, tracked deferral, not a bug. By contrast,
 * {@code expandableScope} <em>is</em> functional: it governs run-time context-expansion grants
 * ({@code RequestContextCommand}) and does count toward compaction reuse.
 *
 * <p>Once implemented, {@code selectors} is intended to be the token-efficient subset chosen
 * <em>within</em> the boundary an inheritance scope decides; it would never widen context beyond
 * that boundary.
 *
 * @param selectors       the context sources the step declares as its scope; <strong>not yet
 *                        enforced at invocation — see the class Javadoc</strong>. Never {@code null}
 *                        ({@code null} becomes an empty list, defensively copied)
 * @param expandableScope the additional sources the step may request; empty means no expansion is
 *                        permitted. Never {@code null} ({@code null} becomes an empty list, copied)
 * @param maxExpansions   maximum number of requested context expansions evaluated across a step
 *                        invocation's whole lifetime — each selector requested via
 *                        {@code RequestContextCommand} counts as one expansion, however the
 *                        selectors are batched into commands or spread across separate
 *                        command-application batches for the same step execution uid (for example an
 *                        {@code AWAITING_TOOL_APPROVAL} pause/resume cycle, or a retry). The count is
 *                        persisted for the step execution uid and survives across those batches; a
 *                        genuinely new step invocation gets a new uid, so its count starts fresh.
 *                        {@code null} defaults to {@value #DEFAULT_MAX_EXPANSIONS}. Must be positive
 *                        when present — "no expansion permitted" is declared with an empty
 *                        {@code expandableScope}, not a zero limit
 */
public record ContextSelection(
    List<ContextSelector> selectors,
    List<ContextSelector> expandableScope,
    Integer maxExpansions
) {

  /**
   * Default maximum requested context expansions per step invocation when {@link #maxExpansions}
   * is {@code null}.
   */
  public static final int DEFAULT_MAX_EXPANSIONS = 1;

  public ContextSelection {
    selectors = selectors != null ? List.copyOf(selectors) : List.of();
    expandableScope = expandableScope != null ? List.copyOf(expandableScope) : List.of();
    if (maxExpansions != null) {
      Validate.isGreaterThanZero(maxExpansions,
          "ContextSelection maxExpansions must be positive when present; declare an empty "
              + "expandableScope to permit no expansion");
    }
  }

  /**
   * Returns the effective maximum requested expansions: {@link #maxExpansions} when present,
   * otherwise {@link #DEFAULT_MAX_EXPANSIONS}.
   *
   * @return the effective maximum; always positive
   */
  public int effectiveMaxExpansions() {
    return maxExpansions != null ? maxExpansions : DEFAULT_MAX_EXPANSIONS;
  }
}
