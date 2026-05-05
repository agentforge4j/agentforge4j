package com.agentforge4j.core.workflow.step.loop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoopConfigTest {

  @Test
  void for_each_requires_context_key() {
    assertThatThrownBy(() -> new LoopConfig(
        LoopTerminationStrategy.FOR_EACH,
        null,
        null,
        1,
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("forEachContextKey");
  }

  @Test
  void evaluator_requires_evaluator_agent_id() {
    assertThatThrownBy(() -> new LoopConfig(
        LoopTerminationStrategy.EVALUATOR,
        null,
        null,
        1,
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("evaluatorAgentId");
  }

  @Test
  void max_iterations_must_be_positive() {
    assertThatThrownBy(() -> new LoopConfig(
        LoopTerminationStrategy.AGENT_SIGNAL,
        null,
        null,
        0,
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxIterations");
  }

  @Test
  void max_iterations_action_defaults_to_await_user() {
    LoopConfig cfg = new LoopConfig(LoopTerminationStrategy.AGENT_SIGNAL, null, null, 1, null);
    assertThat(cfg.maxIterationsAction()).isEqualTo(MaxIterationsAction.AWAIT_USER);
  }

  @Test
  void agent_signal_accepts_null_strategy_specific_fields() {
    LoopConfig cfg = new LoopConfig(LoopTerminationStrategy.AGENT_SIGNAL, null, null, 2, MaxIterationsAction.FAIL);
    assertThat(cfg.forEachContextKey()).isNull();
    assertThat(cfg.evaluatorAgentId()).isNull();
  }
}
