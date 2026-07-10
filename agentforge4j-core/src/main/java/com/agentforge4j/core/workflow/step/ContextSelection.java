// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Optional declaration on a {@link StepDefinition} of the context a step receives by default, and the
 * additional context it <em>may</em> request at run time. A step with no {@link ContextSelection}
 * keeps the full-context behaviour.
 *
 * <p>What the runtime currently does with this declaration: selectors are validated at load time
 * against the enclosing workflow's declared sources, {@code expandableScope} governs run-time
 * context-expansion grants ({@code RequestContextCommand}), and compact-form references count toward
 * compaction-reuse thresholds. The runtime does not yet assemble a step's rendered agent context
 * from {@code selectors} — an agent's input context still flows through its
 * {@code ContextMapping}.
 *
 * <p>This is the token-efficient subset chosen <em>within</em> the boundary an inheritance scope
 * decides; it never widens context beyond that boundary.
 *
 * @param selectors       the context sources the step receives; never {@code null} ({@code null}
 *                        becomes an empty list, defensively copied)
 * @param expandableScope the additional sources the step may request; empty means no expansion is
 *                        permitted. Never {@code null} ({@code null} becomes an empty list, copied)
 * @param maxExpansions   maximum number of requested context expansions evaluated within a single
 *                        command-application batch (one {@code AGENT}/{@code SPAR} call's commands)
 *                        — each selector requested via {@code RequestContextCommand} counts as one
 *                        expansion, however the selectors are batched into commands. The count
 *                        resets on the next batch, so it does not bound expansions across a step's
 *                        full invocation lifecycle if that step pauses and resumes (for example an
 *                        {@code AWAITING_TOOL_APPROVAL} cycle) or is retried — each resumption or
 *                        retry starts a fresh count. {@code null} defaults to
 *                        {@value #DEFAULT_MAX_EXPANSIONS}. Must be positive when present — "no
 *                        expansion permitted" is declared with an empty {@code expandableScope}, not
 *                        a zero limit
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
