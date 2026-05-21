package com.agentforge4j.llm.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmExecutionResponseTest {

  @Test
  void rejects_null_text() {
    assertThatThrownBy(() -> new LlmExecutionResponse(null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text");
  }

  @Test
  void accepts_null_model_used_and_token_usage() {
    LlmExecutionResponse response = new LlmExecutionResponse("payload", null, null);

    assertThat(response.text()).isEqualTo("payload");
    assertThat(response.modelUsed()).isNull();
    assertThat(response.tokenUsage()).isNull();
  }

  @Test
  void preserves_component_order() {
    TokenUsageReport usage = new TokenUsageReport(1, 2, null, null);
    LlmExecutionResponse response =
        new LlmExecutionResponse("text", "gpt-4o-mini", usage);

    assertThat(response.text()).isEqualTo("text");
    assertThat(response.modelUsed()).isEqualTo("gpt-4o-mini");
    assertThat(response.tokenUsage()).isSameAs(usage);
  }
}
