// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.blueprint;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintDefinitionTest {

  private static BlueprintBehaviour behaviour() {
    LoopConfig loop = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, 1, null);
    return new BlueprintBehaviour(loop, StepTransition.AUTO);
  }

  private static StepDefinition oneStep() {
    return StepDefinition.builder()
        .withStepId("s1")
        .withName("S")
        .withBehaviour(new FailBehaviour("r"))
        .build();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejects_blank_blueprint_id(String blueprintId) {
    assertThatThrownBy(() -> new BlueprintDefinition(
        blueprintId,
        "N",
        behaviour(),
        List.of(oneStep())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blueprintId");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void rejects_blank_name(String name) {
    assertThatThrownBy(() -> new BlueprintDefinition("bp1", name, behaviour(), List.of(oneStep())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be blank for blueprint: bp1");
  }

  @Test
  void rejects_null_behaviour() {
    assertThatThrownBy(() -> new BlueprintDefinition("bp1", "N", null, List.of(oneStep())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("behaviour must not be null for blueprint: bp1");
  }

  @Test
  void rejects_empty_steps() {
    assertThatThrownBy(() -> new BlueprintDefinition("bp1", "N", behaviour(), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("steps must not be empty for blueprint: bp1");
  }

  @Test
  void steps_list_is_defensive_copy() {
    List<Executable> steps = new ArrayList<>(List.of(oneStep()));
    BlueprintDefinition bp = new BlueprintDefinition("bp1", "N", behaviour(), steps);
    steps.clear();

    assertThat(bp.steps()).hasSize(1);
    assertThatThrownBy(() -> bp.steps().add(oneStep()))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
