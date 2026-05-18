package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmExecutionRequestTest {

  @Test
  void withDefaultModel_sets_null_model_and_validates_required_fields() {
    LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("ollama", "prompt", "input");

    assertEquals("ollama", request.providerName());
    assertNull(request.model());
    assertEquals("prompt", request.systemPrompt());
    assertEquals("input", request.userInput());
    assertNull(request.maxOutputTokens());
  }

  @Test
  void allows_explicit_null_model_when_other_fields_are_valid() {
    LlmExecutionRequest request = new LlmExecutionRequest("openai", null, "prompt", "input");

    assertEquals("openai", request.providerName());
    assertNull(request.model());
  }

  @Test
  void rejects_blank_provider_name() {
    assertThatThrownBy(() -> new LlmExecutionRequest("  ", "m", "p", "i"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Provider");
  }

  @Test
  void rejects_null_provider_name() {
    assertThatThrownBy(() -> new LlmExecutionRequest(null, "m", "p", "i"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_blank_system_prompt() {
    assertThatThrownBy(() -> new LlmExecutionRequest("openai", "m", "\t", "i"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("System prompt");
  }

  @Test
  void rejects_blank_user_input() {
    assertThatThrownBy(() -> new LlmExecutionRequest("openai", "m", "p", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User input");
  }

  @Test
  void withDefaultModel_rejects_blank_provider() {
    assertThatThrownBy(() -> LlmExecutionRequest.withDefaultModel("", "p", "i"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allows_blank_model_string_when_other_fields_are_valid() {
    LlmExecutionRequest request = new LlmExecutionRequest("openai", "   ", "prompt", "input");

    assertThat(request.model()).isEqualTo("   ");
  }

  @Test
  void allows_positive_max_output_tokens() {
    LlmExecutionRequest request =
        new LlmExecutionRequest("openai", "gpt-4o-mini", "sys", "in", 512);
    assertThat(request.maxOutputTokens()).isEqualTo(512);
  }

  @Test
  void rejects_non_positive_max_output_tokens() {
    assertThatThrownBy(() -> new LlmExecutionRequest("openai", "m", "s", "u", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
