package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;

/**
 * Immutable retry settings for {@link RetryingLlmClient}: attempt cap, decorrelated jitter bounds,
 * and optional total elapsed budget.
 */
public record LlmRetryPolicy(int maxAttempts, long baseBackoffMs, long maxBackoffMs,
                             long maxElapsedMs) {

  public LlmRetryPolicy {
    Validate.isGreaterThanZero(maxAttempts, "maxAttempts must be at least 1");
    Validate.isNotNegative(baseBackoffMs, "baseBackoffMs must be >= 0");
    Validate.isGreaterThan(maxBackoffMs, baseBackoffMs, "maxBackoffMs must be >= baseBackoffMs");
    Validate.isNotNegative(maxElapsedMs, "maxElapsedMs must be >= 0");
  }

  public static LlmRetryPolicy defaults() {
    return new LlmRetryPolicy(3, 200, 10_000, 30_000);
  }
}
