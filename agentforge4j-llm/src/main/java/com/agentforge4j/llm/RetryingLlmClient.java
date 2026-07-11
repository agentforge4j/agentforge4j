// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import com.agentforge4j.util.Validate;
import com.agentforge4j.util.retry.DecorrelatedJitter;
import com.agentforge4j.util.retry.RetryableHttpStatuses;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;

/**
 * Wraps an LLM client with retry logic for transient failures (decorrelated jitter backoff).
 */
public final class RetryingLlmClient implements LlmClient {

  private static final System.Logger LOG = System.getLogger(RetryingLlmClient.class.getName());

  private final LlmClient delegate;
  private final LlmRetryPolicy policy;

  public RetryingLlmClient(LlmClient delegate, LlmRetryPolicy policy) {
    this.delegate = Validate.notNull(delegate, "delegate must not be null");
    this.policy = Validate.notNull(policy, "policy must not be null");
  }

  @Override
  public String getProviderName() {
    return delegate.getProviderName();
  }

  @Override
  public Optional<LlmRetryPolicy> getRetryPolicy() {
    return delegate.getRetryPolicy();
  }

  @Override
  public LlmExecutionResponse execute(LlmExecutionRequest request) {
    LOG.log(System.Logger.Level.DEBUG,
        "LLM retry policy: maxAttempts={0}, baseBackoffMs={1}, maxBackoffMs={2}, maxElapsedMs={3}",
        policy.maxAttempts(), policy.baseBackoffMs(), policy.maxBackoffMs(), policy.maxElapsedMs());

    long startNanos = System.nanoTime();
    long lastSleepMs = policy.baseBackoffMs();
    RuntimeException lastFailure = null;

    for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
      try {
        return delegate.execute(request);
      } catch (RuntimeException ex) {
        lastFailure = ex;
        String provider = delegate.getProviderName();
        boolean transientFailure = isTransient(ex);
        boolean exhausted = attempt >= policy.maxAttempts();
        boolean shouldRetry = !exhausted && transientFailure;

        if (shouldRetry) {
          long elapsedMs = elapsedMillisSince(startNanos);
          if (policy.maxElapsedMs() > 0 && elapsedMs >= policy.maxElapsedMs()) {
            LOG.log(System.Logger.Level.ERROR,
                "retry budget exhausted after {0}ms", elapsedMs);
            throw ex;
          }

          long sleepMs = nextDecorrelatedSleepMs(policy, lastSleepMs);

          if (policy.maxElapsedMs() > 0 && elapsedMs + sleepMs > policy.maxElapsedMs()) {
            LOG.log(System.Logger.Level.ERROR,
                "retry budget exhausted after {0}ms", elapsedMs);
            throw ex;
          }

          LOG.log(System.Logger.Level.WARNING,
              "LLM call failed (attempt {0}/{1}) for provider={2}: {3}. Retrying in {4}ms.",
              attempt, policy.maxAttempts(), provider, String.valueOf(ex), sleepMs);
          sleep(sleepMs);
          lastSleepMs = sleepMs;
        } else if (transientFailure && exhausted) {
          LOG.log(System.Logger.Level.ERROR,
              "LLM call failed after {0} attempts for provider={1}: {2}",
              policy.maxAttempts(), provider, String.valueOf(ex), ex);
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

  private static long nextDecorrelatedSleepMs(LlmRetryPolicy policy, long lastSleepMs) {
    return DecorrelatedJitter.nextDelayMillis(
        policy.baseBackoffMs(), lastSleepMs, policy.maxBackoffMs());
  }

  private static long elapsedMillisSince(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
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
    return httpStatus != null && RetryableHttpStatuses.isRetryable(httpStatus);
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
