// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.retry;

import java.util.Set;

/**
 * The HTTP status codes that both the HTTP tool provider and the LLM retry client treat as
 * transient and safe to retry: {@code 429} (Too Many Requests) and the {@code 5xx} statuses that
 * typically indicate a temporary upstream problem rather than a permanent request error.
 */
public final class RetryableHttpStatuses {

  /**
   * Status codes considered retryable: {@code 429, 500, 502, 503, 504}.
   */
  private static final Set<Integer> CODES = Set.of(429, 500, 502, 503, 504);

  private RetryableHttpStatuses() {
  }

  /**
   * Returns whether the given HTTP status code is retryable.
   *
   * @param statusCode the HTTP status code
   * @return {@code true} if {@code statusCode} is in {@link #CODES}
   */
  public static boolean isRetryable(int statusCode) {
    return CODES.contains(statusCode);
  }
}
