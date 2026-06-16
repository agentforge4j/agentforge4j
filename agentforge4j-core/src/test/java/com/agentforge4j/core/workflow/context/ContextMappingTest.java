// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextMappingTest {

  @Test
  void none_has_empty_key_lists() {
    ContextMapping none = ContextMapping.none();
    assertThat(none.inputKeys()).isEmpty();
    assertThat(none.outputKeys()).isEmpty();
  }

  @Test
  void null_input_and_output_lists_default_to_empty() {
    ContextMapping m = new ContextMapping(null, null);
    assertThat(m.inputKeys()).isEmpty();
    assertThat(m.outputKeys()).isEmpty();
  }

  @Test
  void copies_lists_and_exposes_unmodifiable_views() {
    List<String> in = new ArrayList<>(List.of("a"));
    List<String> out = new ArrayList<>(List.of("b"));
    ContextMapping m = new ContextMapping(in, out);
    in.add("x");

    assertThat(m.inputKeys()).containsExactly("a");
    assertThatThrownBy(() -> m.inputKeys().add("y"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> m.outputKeys().add("z"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
