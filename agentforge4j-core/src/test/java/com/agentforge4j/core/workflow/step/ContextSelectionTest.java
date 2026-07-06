// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSelectionTest {

  @Test
  void nullListsBecomeEmpty() {
    ContextSelection selection = new ContextSelection(null, null, null);

    assertThat(selection.selectors()).isEmpty();
    assertThat(selection.expandableScope()).isEmpty();
  }

  @Test
  void copiesSelectorsDefensively() {
    ContextSelector selector = new ContextSelector(ContextSourceKind.STEP_OUTPUT, "step-1",
        ContextVariant.FULL);
    ContextSelection selection = new ContextSelection(List.of(selector), List.of(), null);

    assertThat(selection.selectors()).containsExactly(selector);
    assertThat(selection.expandableScope()).isEmpty();
  }

  @Test
  void effectiveMaxExpansionsDefaultsWhenNullOrNonPositive() {
    assertThat(new ContextSelection(null, null, null).effectiveMaxExpansions())
        .isEqualTo(ContextSelection.DEFAULT_MAX_EXPANSIONS);
    assertThat(new ContextSelection(null, null, 0).effectiveMaxExpansions())
        .isEqualTo(ContextSelection.DEFAULT_MAX_EXPANSIONS);
    assertThat(new ContextSelection(null, null, -1).effectiveMaxExpansions())
        .isEqualTo(ContextSelection.DEFAULT_MAX_EXPANSIONS);
  }

  @Test
  void effectiveMaxExpansionsUsesPositiveValue() {
    assertThat(new ContextSelection(null, null, 3).effectiveMaxExpansions()).isEqualTo(3);
  }
}
