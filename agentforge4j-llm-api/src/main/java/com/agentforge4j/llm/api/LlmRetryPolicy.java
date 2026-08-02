// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.api;

import com.agentforge4j.util.Validate;

/**
 * Immutable retry settings for a retrying LLM client: attempt cap, decorrelated jitter bounds, and
 * optional total elapsed budget.
 */
public record LlmRetryPolicy(int maxAttempts, long baseBackoffMs, long maxBackoffMs,
                             long maxElapsedMs) {

  /**
   * Creates a retry policy with the given parameters, validating that they are within expected
   * bounds.
   *
   * @param maxAttempts   Max retry attempts (must be >= 1)
   * @param baseBackoffMs Base backoff in ms for decorrelated jitter (must be >= 0)
   * @param maxBackoffMs  Max backoff in ms for decorrelated jitter (must be >= baseBackoffMs)
   * @param maxElapsedMs  Optional total elapsed budget in ms for all attempts (must be >= 0)
   */
  public LlmRetryPolicy {
    Validate.isGreaterThanZero(maxAttempts, "maxAttempts must be at least 1");
    Validate.isNotNegative(baseBackoffMs, "baseBackoffMs must be >= 0");
    Validate.isGreaterThan(maxBackoffMs, baseBackoffMs, "maxBackoffMs must be >= baseBackoffMs");
    Validate.isNotNegative(maxElapsedMs, "maxElapsedMs must be >= 0");
  }

  /**
   * Default retry policy: 3 attempts, 200ms base backoff, 10s max backoff, 30s total elapsed
   * budget.
   */
  public static LlmRetryPolicy defaults() {
    return new LlmRetryPolicy(3, 200, 10_000, 30_000);
  }
}
