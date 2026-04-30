package com.agentforge4j.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class LlmInvocationExceptionTest {

  @Test
  void should_construct_with_message() {
    String message = "OpenAI request failed";
    LlmInvocationException exception = new LlmInvocationException(message);

    assertEquals(message, exception.getMessage());
    assertNull(exception.getCause());
  }

  @Test
  void should_construct_with_message_and_cause() {
    String message = "OpenAI request failed";
    IOException cause = new IOException("Connection timeout");
    LlmInvocationException exception = new LlmInvocationException(message, cause);

    assertEquals(message, exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void should_be_runtime_exception() {
    LlmInvocationException exception = new LlmInvocationException("test");
    assertInstanceOf(RuntimeException.class, exception);
  }

  @Test
  void should_throw_and_catch_as_runtime_exception() {
    assertThrows(LlmInvocationException.class, () -> {
      throw new LlmInvocationException("test message");
    });
  }

  @Test
  void should_preserve_cause_chain() {
    IOException ioException = new IOException("Network error");
    LlmInvocationException exception = new LlmInvocationException("Request failed", ioException);

    assertEquals(ioException, exception.getCause());
    assertSame(ioException, exception.getCause());
  }

  @Test
  void should_support_null_message() {
    LlmInvocationException exception = new LlmInvocationException(null);
    assertNull(exception.getMessage());
  }

  @Test
  void should_support_empty_message() {
    LlmInvocationException exception = new LlmInvocationException("");
    assertEquals("", exception.getMessage());
  }
}


