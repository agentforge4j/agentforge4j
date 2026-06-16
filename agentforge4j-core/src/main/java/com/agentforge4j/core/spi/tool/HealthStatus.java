// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * Health of a {@link ToolProvider}.
 *
 * @param state  non-null health state
 * @param detail human-readable detail, or {@code null}
 */
public record HealthStatus(State state, String detail) {

  /**
   * Validates that {@code state} is non-null.
   */
  public HealthStatus {
    Validate.notNull(state, "HealthStatus state must not be null");
  }

  /**
   * Provider health state.
   */
  public enum State {
    UP,
    DEGRADED,
    DOWN
  }
}
