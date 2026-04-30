package com.agentforge4j.llm;

/**
 * Thrown when an LLM request fails due to network issues, HTTP errors, invalid responses, or
 * providerName-specific errors.
 */
public class LlmInvocationException extends RuntimeException {

  /**
   * Creates a new exception with the given message.
   *
   * @param message a description of the failure
   */
  public LlmInvocationException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with the given message and cause.
   *
   * @param message a description of the failure
   * @param cause   the underlying exception
   */
  public LlmInvocationException(String message, Throwable cause) {
    super(message, cause);
  }
}
