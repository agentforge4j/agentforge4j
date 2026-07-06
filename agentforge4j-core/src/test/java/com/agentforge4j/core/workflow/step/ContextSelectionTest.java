// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSelectionTest {

  @Test
  void nullListsBecomeEmpty() {
    ContextSelection selection = new ContextSelection(null, null);

    assertThat(selection.selectors()).isEmpty();
    assertThat(selection.expandableScope()).isEmpty();
  }

  @Test
  void copiesSelectorsDefensively() {
    ContextSelector selector = new ContextSelector(ContextSourceKind.STEP_OUTPUT, "step-1",
        ContextVariant.FULL);
    ContextSelection selection = new ContextSelection(List.of(selector), List.of());

    assertThat(selection.selectors()).containsExactly(selector);
    assertThat(selection.expandableScope()).isEmpty();
  }
}
