package com.agentforge4j.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LlmExecutionRequestTest {

  @Test
  void should_construct_with_all_fields() {
    LlmExecutionRequest request = new LlmExecutionRequest("openai", "gpt-4", "prompt", "input");
    assertEquals("openai", request.providerName());
    assertEquals("gpt-4", request.model());
    assertEquals("prompt", request.systemPrompt());
    assertEquals("input", request.userInput());
  }

  @Test
  void should_construct_with_null_model() {
    LlmExecutionRequest request = new LlmExecutionRequest("openai", null, "prompt", "input");
    assertEquals("openai", request.providerName());
    assertNull(request.model());
    assertEquals("prompt", request.systemPrompt());
    assertEquals("input", request.userInput());
  }

  @Test
  void should_create_with_default_model_factory() {
    LlmExecutionRequest request = LlmExecutionRequest.withDefaultModel("ollama", "prompt", "input");
    assertEquals("ollama", request.providerName());
    assertNull(request.model());
    assertEquals("prompt", request.systemPrompt());
    assertEquals("input", request.userInput());
  }

  @Test
  void should_have_correct_equality() {
    LlmExecutionRequest r1 = new LlmExecutionRequest("openai", "gpt-4", "p", "i");
    LlmExecutionRequest r2 = new LlmExecutionRequest("openai", "gpt-4", "p", "i");
    assertEquals(r1, r2);
  }

  @Test
  void should_have_correct_inequality_on_different_provider() {
    LlmExecutionRequest r1 = new LlmExecutionRequest("openai", "gpt-4", "p", "i");
    LlmExecutionRequest r2 = new LlmExecutionRequest("claude", "gpt-4", "p", "i");
    assertNotEquals(r1, r2);
  }

  @Test
  void should_have_equal_hashcode_for_equal_objects() {
    LlmExecutionRequest r1 = new LlmExecutionRequest("openai", "gpt-4", "p", "i");
    LlmExecutionRequest r2 = new LlmExecutionRequest("openai", "gpt-4", "p", "i");
    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  void should_construct_with_empty_strings() {
    assertThrows(IllegalArgumentException.class, () -> {
      new LlmExecutionRequest("", "", "", "");
    });
  }
}
