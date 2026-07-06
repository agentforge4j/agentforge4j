// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Verifies the net-new execution-estimation hint fields on {@link StepDefinition} and
 * {@link LoopConfig}: their validation invariants and that they bind from JSON by component name.
 */
class EstimationHintFieldsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void stepDefinitionCarriesTokenHintsThroughBuilder() {
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1").withName("S1").withBehaviour(new FailBehaviour("stop"))
        .withEstimatedInputTokens(123)
        .withEstimatedOutputTokens(456)
        .build();

    assertThat(step.estimatedInputTokens()).isEqualTo(123);
    assertThat(step.estimatedOutputTokens()).isEqualTo(456);
  }

  @Test
  void stepDefinitionRejectsNegativeTokenHint() {
    assertThatThrownBy(() -> StepDefinition.builder()
        .withStepId("s1").withName("S1").withBehaviour(new FailBehaviour("stop"))
        .withEstimatedInputTokens(-1)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("estimatedInputTokens");
  }

  @Test
  void stepDefinitionTokenHintsBindFromJson() throws Exception {
    String json = "{\"kind\":\"STEP\",\"stepId\":\"s1\",\"name\":\"S1\","
        + "\"behaviour\":{\"type\":\"FAIL\",\"reason\":\"stop\"},"
        + "\"estimatedInputTokens\":123,\"estimatedOutputTokens\":456}";

    StepDefinition step = MAPPER.readValue(json, StepDefinition.class);

    assertThat(step.estimatedInputTokens()).isEqualTo(123);
    assertThat(step.estimatedOutputTokens()).isEqualTo(456);
  }

  @Test
  void loopConfigExpectedIterationsRoundTripsThroughJson() throws Exception {
    LoopConfig loop = new LoopConfig(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 10, MaxIterationsAction.FAIL, false, 2);

    LoopConfig restored = MAPPER.readValue(MAPPER.writeValueAsString(loop), LoopConfig.class);

    assertThat(restored.expectedIterations()).isEqualTo(2);
    assertThat(restored.maxIterations()).isEqualTo(10);
  }

  @Test
  void loopConfigRejectsExpectedIterationsExceedingMax() {
    assertThatThrownBy(() -> new LoopConfig(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 3, MaxIterationsAction.FAIL, false, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expectedIterations");
  }
}
