package com.agentforge4j.llm.api;

/**
 * Thrown when an LLM request fails due to network issues, HTTP errors, invalid responses, or
 * provider-specific validation failures.
 */
public final class LlmInvocationException extends RuntimeException {

  private final Integer httpStatus;

  /**
   * Creates a new exception with the given message.
   *
   * @param message a description of the failure
   */
  public LlmInvocationException(String message) {
    super(message);
    this.httpStatus = null;
  }

  /**
   * Creates a new exception with the given message and cause.
   *
   * @param message a description of the failure
   * @param cause   the underlying exception
   */
  public LlmInvocationException(String message, Throwable cause) {
    super(message, cause);
    this.httpStatus = null;
  }

  /**
   * Creates a new exception with the given message and HTTP status from the provider response.
   *
   * @param message    a description of the failure
   * @param httpStatus HTTP status code when the failure was triggered by an HTTP response
   */
  public LlmInvocationException(String message, int httpStatus) {
    super(message);
    this.httpStatus = httpStatus;
  }

  /**
   * Returns the HTTP status code associated with this exception, if applicable.
   *
   * @return the HTTP status code, or null if not applicable
   */
  public Integer getHttpStatus() {
    return httpStatus;
  }
}
