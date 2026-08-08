// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void effectiveMaxExpansionsDefaultsWhenNull() {
    assertThat(new ContextSelection(null, null, null).effectiveMaxExpansions())
        .isEqualTo(ContextSelection.DEFAULT_MAX_EXPANSIONS);
  }

  @Test
  void rejectsNonPositiveMaxExpansions() {
    assertThatThrownBy(() -> new ContextSelection(null, null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxExpansions");
    assertThatThrownBy(() -> new ContextSelection(null, null, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxExpansions");
  }

  @Test
  void effectiveMaxExpansionsUsesPositiveValue() {
    assertThat(new ContextSelection(null, null, 3).effectiveMaxExpansions()).isEqualTo(3);
  }
}
