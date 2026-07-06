// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

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
 */
public record ContextSelection(
    List<ContextSelector> selectors,
    List<ContextSelector> expandableScope
) {

  public ContextSelection {
    selectors = selectors != null ? List.copyOf(selectors) : List.of();
    expandableScope = expandableScope != null ? List.copyOf(expandableScope) : List.of();
  }
}
