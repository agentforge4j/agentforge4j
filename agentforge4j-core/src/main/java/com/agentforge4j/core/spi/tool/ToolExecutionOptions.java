// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Tunables for tool invocation. The execution service enforces an authoritative hard timeout
 * derived from {@link #timeout()}; providers may honour these values best-effort.
 *
 * @param timeout      hard per-invocation timeout; non-null and positive
 * @param maxRetries   maximum retry attempts after the first; never negative
 * @param retryBackoff delay between retries; non-null and never negative
 */
public record ToolExecutionOptions(Duration timeout, int maxRetries, Duration retryBackoff) {

  /**
   * Validates the timeout, retry count, and backoff.
   */
  public ToolExecutionOptions {
    Validate.notNull(timeout, "ToolExecutionOptions timeout must not be null");
    Validate.isTrue(!timeout.isNegative() && !timeout.isZero(),
        "ToolExecutionOptions timeout must be positive");
    Validate.isNotNegative(maxRetries, "ToolExecutionOptions maxRetries must not be negative");
    Validate.notNull(retryBackoff, "ToolExecutionOptions retryBackoff must not be null");
    Validate.isTrue(!retryBackoff.isNegative(),
        "ToolExecutionOptions retryBackoff must not be negative");
  }

  /**
   * @return defaults: 30s timeout, no retries, zero backoff
   */
  public static ToolExecutionOptions defaults() {
    return new ToolExecutionOptions(Duration.ofSeconds(30), 0, Duration.ZERO);
  }
}
