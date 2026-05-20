package com.agentforge4j.llm.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmExecutionResponseTest {

  @Test
  void rejects_null_text() {
    assertThatThrownBy(() -> new LlmExecutionResponse(null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("text");
  }

  @Test
  void accepts_null_token_usage() {
    LlmExecutionResponse response = new LlmExecutionResponse("payload", null);

    assertThat(response.text()).isEqualTo("payload");
    assertThat(response.tokenUsage()).isNull();
  }
}
