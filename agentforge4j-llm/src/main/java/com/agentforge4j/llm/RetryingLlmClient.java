package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps an LLM client with retry logic for transient failures.
 * <p>
 * Retries on network errors, timeouts, and specific HTTP status codes (429, 5xx). Uses exponential
 * backoff with a fixed delay multiplier.
 */
public final class RetryingLlmClient implements LlmClient {

  private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile(
      "\\bHTTP error:\\s*(\\d{3})\\b");
  private static final Set<Integer> RETRYABLE_HTTP_STATUS = Set.of(429, 500, 502, 503, 504);

  private final LlmClient delegate;
  private final int maxAttempts;
  private final long backoffMs;

  /**
   * Creates a retrying client with the specified parameters.
   *
   * @param delegate    the underlying LLM client to retry
   * @param maxAttempts the maximum number of attempts (must be at least 1)
   * @param backoffMs   the base backoff delay in milliseconds (must be >= 0)
   */
  public RetryingLlmClient(LlmClient delegate, int maxAttempts, long backoffMs) {
    this.delegate = Validate.notNull(delegate, "delegate must not be null");
    Validate.isTrue(maxAttempts >= 1, "Claude maxTokenSize must be positive");
    Validate.isTrue(backoffMs >= 0, "backoffMs must be >= 0");
    this.maxAttempts = maxAttempts;
    this.backoffMs = backoffMs;
  }

  @Override
  public String getProviderName() {
    return delegate.getProviderName();
  }

  /**
   * Executes an LLM request with retry logic.
   * <p>
   * Retries on transient failures up to {@code maxAttempts} times with exponential backoff.
   *
   * @param request the LLM execution request
   * @return the response from the LLM provider
   * @throws LlmInvocationException if all attempts fail
   */
  @Override
  public String execute(LlmExecutionRequest request) {
    RuntimeException lastFailure = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return delegate.execute(request);
      } catch (RuntimeException ex) {
        lastFailure = ex;
        boolean shouldRetry = attempt < maxAttempts && isTransient(ex);
        if (!shouldRetry) {
          throw ex;
        }
        sleep(attempt * backoffMs);
      }
    }
    throw Validate.notNull(lastFailure, "lastFailure must not be null");
  }

  private static boolean isTransient(RuntimeException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof IOException
          || cause instanceof HttpTimeoutException
          || cause instanceof SocketTimeoutException
          || cause instanceof ConnectException) {
        return true;
      }
      cause = cause.getCause();
    }
    if (!(exception instanceof LlmInvocationException invocationException)) {
      return false;
    }
    Matcher matcher = HTTP_STATUS_PATTERN.matcher(invocationException.getMessage());
    if (!matcher.find()) {
      return false;
    }
    return RETRYABLE_HTTP_STATUS.contains(Integer.parseInt(matcher.group(1)));
  }

  private static void sleep(long durationMs) {
    if (durationMs <= 0) {
      return;
    }
    try {
      Thread.sleep(durationMs);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw new LlmInvocationException("Retry interrupted", interruptedException);
    }
  }
}
