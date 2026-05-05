package com.agentforge4j.core.command;

import com.agentforge4j.core.command.schema.LlmCommandSubtypeRegistry;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmCommandSubtypeRegistryTest {

  @Test
  void all_subtypes_reflect_llm_command_json_sub_types_annotation() {
    JsonSubTypes declared = LlmCommand.class.getAnnotation(JsonSubTypes.class);
    assertThat(declared).isNotNull();

    var map = LlmCommandSubtypeRegistry.allSubtypes();
    assertThat(map).hasSize(declared.value().length);

    for (JsonSubTypes.Type t : declared.value()) {
      @SuppressWarnings("unchecked")
      Class<? extends LlmCommand> expected = (Class<? extends LlmCommand>) t.value();
      assertThat(map.get(t.name())).isEqualTo(expected);
    }
  }

  @Test
  void all_type_names_ordered_matches_map_iteration_order() {
    assertThat(LlmCommandSubtypeRegistry.allTypeNamesOrdered())
        .containsExactlyElementsOf(LlmCommandSubtypeRegistry.allSubtypes().keySet());
  }

  @Test
  void subtype_map_is_unmodifiable() {
    assertThatThrownBy(() -> LlmCommandSubtypeRegistry.allSubtypes().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
