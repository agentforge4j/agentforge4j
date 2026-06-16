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
    assertThatThrownBy(() -> new ContextValueList(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("values");
  }

  @Test
  void copies_values_list() {
    var inner = new StringContextValue("a");
    var list = new ArrayList<ContextValue>(List.of(inner));
    var wrapped = new ContextValueList(list);
    list.clear();

    assertThat(wrapped.values()).containsExactly(inner);
    assertThatThrownBy(() -> wrapped.values().add(new StringContextValue("b")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
