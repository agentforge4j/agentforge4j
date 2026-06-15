package com.agentforge4j.llm.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmExecutionRequestTest {

  @Test
  void six_arg_constructor_leaves_identity_null() {
    LlmExecutionRequest request = new LlmExecutionRequest("openai", "gpt-4o-mini", "system", "user", 256, null);

    assertThat(request.identity()).isNull();
    assertThat(request.maxOutputTokens()).isEqualTo(256);
    assertThat(request.promptLayerBoundaries()).isNull();
  }

  @Test
  void four_arg_convenience_constructor_leaves_optional_fields_null() {
    LlmExecutionRequest request = new LlmExecutionRequest("openai", "gpt-4o-mini", "system", "user");

    assertThat(request.maxOutputTokens()).isNull();
    assertThat(request.promptLayerBoundaries()).isNull();
    assertThat(request.identity()).isNull();
  }

  @Test
  void canonical_constructor_carries_identity() {
    LlmInvocationIdentity identity = new LlmInvocationIdentity("recruitment", "run-1", "screen-cv", "screener");
    LlmExecutionRequest request = new LlmExecutionRequest("fake", null, "system", "user", null, null, identity);

    assertThat(request.identity()).isSameAs(identity);
  }

  @Test
  void with_default_model_omits_model_and_identity() {
    LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("fake", "system", "user");

    assertThat(request.model()).isNull();
    assertThat(request.identity()).isNull();
  }

  @Test
  void rejects_blank_provider() {
    assertThatThrownBy(() -> new LlmExecutionRequest(" ", null, "system", "user")).isInstanceOf(
        IllegalArgumentException.class).hasMessageContaining("Provider");
  }

  @Test
  void rejects_non_positive_max_output_tokens() {
    assertThatThrownBy(() -> new LlmExecutionRequest("openai", null, "system", "user", 0)).isInstanceOf(
        IllegalArgumentException.class).hasMessageContaining("maxOutputTokens");
  }
}
