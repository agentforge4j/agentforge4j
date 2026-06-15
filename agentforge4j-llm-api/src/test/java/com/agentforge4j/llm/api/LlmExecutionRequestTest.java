package com.agentforge4j.llm.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmExecutionRequestTest {

  @Test
  void canonical_constructor_leaves_optional_fields_null() {
    LlmExecutionRequest request = new LlmExecutionRequest("openai", "gpt-4o-mini", "system", "user", null, null, null);

    assertThat(request.model()).isEqualTo("gpt-4o-mini");
    assertThat(request.maxOutputTokens()).isNull();
    assertThat(request.promptLayerBoundaries()).isNull();
    assertThat(request.identity()).isNull();
  }

  @Test
  void canonical_constructor_carries_all_optional_fields() {
    LlmInvocationIdentity identity = new LlmInvocationIdentity("recruitment", "run-1", "screen-cv", "screener");
    LlmExecutionRequest request =
        new LlmExecutionRequest("fake", null, "system", "user", 256, null, identity);

    assertThat(request.maxOutputTokens()).isEqualTo(256);
    assertThat(request.promptLayerBoundaries()).isNull();
    assertThat(request.identity()).isSameAs(identity);
  }

  @Test
  void null_model_selects_client_default() {
    LlmExecutionRequest request = new LlmExecutionRequest("fake", null, "system", "user", null, null, null);

    assertThat(request.model()).isNull();
    assertThat(request.maxOutputTokens()).isNull();
    assertThat(request.promptLayerBoundaries()).isNull();
    assertThat(request.identity()).isNull();
  }

  @Test
  void rejects_blank_provider() {
    assertThatThrownBy(() -> new LlmExecutionRequest(" ", null, "system", "user", null, null, null)).isInstanceOf(
        IllegalArgumentException.class).hasMessageContaining("Provider");
  }

  @Test
  void rejects_non_positive_max_output_tokens() {
    assertThatThrownBy(() -> new LlmExecutionRequest("openai", null, "system", "user", 0, null, null))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxOutputTokens");
  }
}
