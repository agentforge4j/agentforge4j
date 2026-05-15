package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Set;

/**
 * Wraps an LLM client with retry logic for transient failures.
 * <p>
 * Retries on network errors, timeouts, and specific HTTP status codes (429, 5xx). Uses linear
 * backoff with a fixed delay multiplier ({@code attempt * backoffMs} between attempts).
 */
public final class RetryingLlmClient implements LlmClient {

  private static final System.Logger LOG = System.getLogger(RetryingLlmClient.class.getName());

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
    Validate.isTrue(maxAttempts >= 1, "maxAttempts must be at least 1");
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
   * Retries on transient failures up to {@code maxAttempts} times with linear backoff
   * ({@code attempt * backoffMs} milliseconds before the next attempt).
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
        String provider = delegate.getProviderName();
        boolean transientFailure = isTransient(ex);
        boolean exhausted = attempt >= maxAttempts;
        boolean shouldRetry = !exhausted && transientFailure;
        if (shouldRetry) {
          long sleepMs = attempt * backoffMs;
          LOG.log(System.Logger.Level.WARNING,
              "LLM call failed (attempt {0}/{1}) for provider={2}: {3}. Retrying in {4}ms.",
              attempt, maxAttempts, provider, String.valueOf(ex), sleepMs);
          sleep(sleepMs);
        } else if (transientFailure && exhausted) {
          LOG.log(System.Logger.Level.ERROR,
              "LLM call failed after {0} attempts for provider={1}: {2}",
              maxAttempts, provider, String.valueOf(ex), ex);
          throw ex;
        } else {
          LOG.log(System.Logger.Level.DEBUG,
              "LLM call failed with non-transient error for provider={0}: {1}",
              provider, String.valueOf(ex));
          throw ex;
        }
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
    Integer httpStatus = invocationException.getHttpStatus();
    return httpStatus != null && RETRYABLE_HTTP_STATUS.contains(httpStatus);
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
