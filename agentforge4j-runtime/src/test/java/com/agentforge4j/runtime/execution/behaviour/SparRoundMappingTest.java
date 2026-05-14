package com.agentforge4j.runtime.execution.behaviour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.runtime.execution.behaviour.handler.SparBehaviourHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SparBehaviourHandler#buildRoundMapping(ContextMapping, int)}. */
class SparRoundMappingTest {

  @Test
  void round_one_mapping_is_new_instance_with_same_keys_as_original() {
    ContextMapping original = new ContextMapping(List.of("workflow.input"), List.of("out"));
    ContextMapping round1 = SparBehaviourHandler.buildRoundMapping(original, 0);
    assertThat(round1).isNotSameAs(original);
    assertThat(round1.inputKeys()).isEqualTo(original.inputKeys());
    assertThat(round1.outputKeys()).isEqualTo(original.outputKeys());
  }

  @Test
  void round_two_adds_round_one_outputs_to_inputs() {
    ContextMapping original = new ContextMapping(List.of("base"), List.of("out"));
    ContextMapping m = SparBehaviourHandler.buildRoundMapping(original, 1);
    assertThat(m).isNotSameAs(original);
    assertThat(m.outputKeys()).isEqualTo(original.outputKeys());
    assertThat(m.inputKeys()).containsExactly(
        "base",
        SparBehaviourHandler.SPAR_PRIMARY_PREFIX + "1",
        SparBehaviourHandler.SPAR_CHALLENGER_PREFIX + "1");
  }

  @Test
  void round_three_adds_rounds_one_and_two_outputs() {
    ContextMapping original = new ContextMapping(List.of("x"), List.of());
    ContextMapping m = SparBehaviourHandler.buildRoundMapping(original, 2);
    assertThat(m).isNotSameAs(original);
    assertThat(m.inputKeys()).containsExactly(
        "x",
        SparBehaviourHandler.SPAR_PRIMARY_PREFIX + "1",
        SparBehaviourHandler.SPAR_CHALLENGER_PREFIX + "1",
        SparBehaviourHandler.SPAR_PRIMARY_PREFIX + "2",
        SparBehaviourHandler.SPAR_CHALLENGER_PREFIX + "2");
  }

  @Test
  void widening_does_not_mutate_original_input_key_list_content() {
    ContextMapping original = new ContextMapping(List.of("only"), List.of("o"));
    SparBehaviourHandler.buildRoundMapping(original, 2);
    assertThat(original.inputKeys()).containsExactly("only");
    assertThat(original.outputKeys()).containsExactly("o");
  }

  @Test
  void built_mapping_exposes_unmodifiable_input_keys() {
    ContextMapping built =
        SparBehaviourHandler.buildRoundMapping(new ContextMapping(List.of("k"), List.of()), 0);
    assertThatThrownBy(() -> built.inputKeys().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejects_negative_previous_rounds() {
    ContextMapping original = ContextMapping.none();
    assertThatThrownBy(() -> SparBehaviourHandler.buildRoundMapping(original, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("previousRounds");
  }
}
