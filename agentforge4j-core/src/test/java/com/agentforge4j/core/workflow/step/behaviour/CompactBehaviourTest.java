// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.core.workflow.step.ContextSourceKind;
import com.agentforge4j.core.workflow.step.ContextVariant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactBehaviourTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static ContextSelector source() {
    return new ContextSelector(ContextSourceKind.LEDGER_SECTION, "requirements", ContextVariant.FULL);
  }

  @Test
  void rejectsNullFields() {
    assertThatThrownBy(
        () -> new CompactBehaviour(null, new DeterministicExtract(), new CompactionPolicy(0, 0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CompactBehaviour(source(), null, new CompactionPolicy(0, 0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CompactBehaviour(source(), new DeterministicExtract(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deserializesFromCompactType() throws Exception {
    CompactBehaviour original = new CompactBehaviour(source(), new LlmSummary("STANDARD"),
        new CompactionPolicy(100, 1));
    String json = mapper.writeValueAsString((StepBehaviour) original);

    assertThat(json).contains("COMPACT");
    StepBehaviour parsed = mapper.readValue(json, StepBehaviour.class);
    assertThat(parsed).isInstanceOf(CompactBehaviour.class);
    CompactBehaviour compact = (CompactBehaviour) parsed;
    assertThat(compact.mode()).isInstanceOf(LlmSummary.class);
    assertThat(compact.policy().minDownstreamReuse()).isEqualTo(1);
    assertThat(compact.source().ref()).isEqualTo("requirements");
  }
}
