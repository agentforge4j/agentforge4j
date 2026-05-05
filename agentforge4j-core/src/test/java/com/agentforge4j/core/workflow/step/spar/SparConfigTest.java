package com.agentforge4j.core.workflow.step.spar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SparConfigTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejects_blank_challenger_id(String challenger) {
    assertThatThrownBy(() -> new SparConfig(challenger, 1, "prompt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("challengerAgentId");
  }

  @Test
  void rejects_max_rounds_below_one() {
    assertThatThrownBy(() -> new SparConfig("c", 0, "prompt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxRounds");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void rejects_blank_resolution_prompt(String prompt) {
    assertThatThrownBy(() -> new SparConfig("c", 2, prompt))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resolutionPrompt");
  }
}
