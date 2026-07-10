// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Optional declaration on a {@link StepDefinition} of the context a step receives by default, and the
 * additional context it <em>may</em> request at run time. The runtime builds the step's context
 * strictly from {@code selectors}; a step with no {@link ContextSelection} keeps the current
 * full-context behaviour.
 *
 * <p>This is the token-efficient subset chosen <em>within</em> the boundary an inheritance scope
 * decides; it never widens context beyond that boundary.
 *
 * @param selectors       the context sources the step receives; never {@code null} ({@code null}
 *                        becomes an empty list, defensively copied)
 * @param expandableScope the additional sources the step may request; empty means no expansion is
 *                        permitted. Never {@code null} ({@code null} becomes an empty list, copied)
 * @param maxExpansions   maximum number of {@code RequestContextCommand} rounds granted or denied
 *                        within a single step invocation; {@code null} defaults to
 *                        {@value #DEFAULT_MAX_EXPANSIONS}. Must be positive when present — "no
 *                        expansion permitted" is declared with an empty {@code expandableScope},
 *                        not a zero limit
 */
public record ContextSelection(
    List<ContextSelector> selectors,
    List<ContextSelector> expandableScope,
    Integer maxExpansions
) {

  /**
   * Default maximum context-expansion rounds per step invocation when {@link #maxExpansions} is
   * {@code null}.
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
   * Returns the effective maximum expansion rounds: {@link #maxExpansions} when present, otherwise
   * {@link #DEFAULT_MAX_EXPANSIONS}.
   *
   * @return the effective maximum; always positive
   */
  public int effectiveMaxExpansions() {
    return maxExpansions != null ? maxExpansions : DEFAULT_MAX_EXPANSIONS;
  }
}
