// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextValueListTest {

  @Test
  void rejects_null_values() {
    assertThatThrownBy(() -> new ContextValueList(null, ContextProvenance.USER_SUPPLIED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("values");
  }

  @Test
  void rejects_null_provenance() {
    assertThatThrownBy(() -> new ContextValueList(List.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provenance");
  }

  @Test
  void copies_values_list() {
    var inner = new StringContextValue("a", ContextProvenance.USER_SUPPLIED);
    var list = new ArrayList<ContextValue>(List.of(inner));
    var wrapped = new ContextValueList(list, ContextProvenance.USER_SUPPLIED);
    list.clear();

    assertThat(wrapped.values()).containsExactly(inner);
    assertThatThrownBy(() -> wrapped.values().add(
        new StringContextValue("b", ContextProvenance.USER_SUPPLIED)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
