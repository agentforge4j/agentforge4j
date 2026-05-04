package com.agentforge4j.core.workflow.step;

import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepDefinitionTest {

  @Test
  void null_context_mapping_defaults_to_none() {
    StepDefinition step = new StepDefinition(
        "s1",
        "Name",
        new FailBehaviour("r"),
        null,
        null);

    assertThat(step.contextMapping()).isEqualTo(ContextMapping.none());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejects_blank_step_id(String stepId) {
    assertThatThrownBy(() -> new StepDefinition(
        stepId,
        "N",
        new FailBehaviour("r"),
        ContextMapping.none(),
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("StepDefinition stepId must not be blank");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void rejects_blank_name(String name) {
    assertThatThrownBy(() -> new StepDefinition(
        "s1",
        name,
        new FailBehaviour("r"),
        null,
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("StepDefinition name must not be blank for step: s1");
  }

  @Test
  void rejects_null_behaviour() {
    assertThatThrownBy(() -> new StepDefinition("s1", "N", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("StepDefinition behaviour must not be null for step: s1");
  }

  @Test
  void preserves_explicit_context_mapping() {
    var mapping = new ContextMapping(List.of("in"), List.of("out"));
    StepDefinition step = new StepDefinition(
        "s1",
        "N",
        new AgentBehaviour("a", StepTransition.AUTO, null),
        mapping,
        null);

    assertThat(step.contextMapping()).isEqualTo(mapping);
  }
}
