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

  @Test
  void rejects_reserved_namespace_output_keys() {
    // A declared output key authorizes external writers (LLM commands, end-user answers) to write
    // that bare key — the reserved runtime namespace must never be declarable.
    assertThatThrownBy(() -> new ContextMapping(List.of(), List.of("__retry_policy_attempts:x")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved")
        .hasMessageContaining("__retry_policy_attempts:x");
  }

  @Test
  void allows_non_reserved_underscore_output_keys() {
    ContextMapping single = new ContextMapping(List.of(), List.of("_x"));
    ContextMapping interior = new ContextMapping(List.of(), List.of("a__b"));
    assertThat(single.outputKeys()).containsExactly("_x");
    assertThat(interior.outputKeys()).containsExactly("a__b");
  }

  @Test
  void reserved_input_keys_remain_declarable() {
    // Input keys only authorize reads; declaring a reserved key there stays legal (e.g. exposing
    // the __llm_tokens_total counter to a step).
    ContextMapping m = new ContextMapping(List.of("__llm_tokens_total"), List.of());
    assertThat(m.inputKeys()).containsExactly("__llm_tokens_total");
  }
}
