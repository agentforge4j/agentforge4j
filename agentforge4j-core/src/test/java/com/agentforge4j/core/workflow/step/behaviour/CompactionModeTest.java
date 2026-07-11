// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactionModeTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void roundTripsDeterministicExtractByType() throws Exception {
    String json = mapper.writeValueAsString(new DeterministicExtract());

    assertThat(json).contains("DETERMINISTIC_EXTRACT");
    assertThat(mapper.readValue(json, CompactionMode.class)).isInstanceOf(DeterministicExtract.class);
  }

  @Test
  void roundTripsLlmSummaryWithTierAndAgentRef() throws Exception {
    String json = mapper.writeValueAsString(new LlmSummary("STANDARD", "summarizer-agent"));

    assertThat(json).contains("LLM_SUMMARY");
    CompactionMode parsed = mapper.readValue(json, CompactionMode.class);
    assertThat(parsed).isInstanceOf(LlmSummary.class);
    assertThat(((LlmSummary) parsed).modelTier()).isEqualTo("STANDARD");
    assertThat(((LlmSummary) parsed).agentRef()).isEqualTo("summarizer-agent");
  }

  @Test
  void llmSummaryRejectsBlankTier() {
    assertThatThrownBy(() -> new LlmSummary(" ", "summarizer-agent"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void llmSummaryRejectsBlankAgentRef() {
    assertThatThrownBy(() -> new LlmSummary("STANDARD", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
