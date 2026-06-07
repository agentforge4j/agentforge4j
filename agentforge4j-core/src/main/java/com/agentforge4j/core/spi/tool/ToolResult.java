package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * Outcome of a single provider invocation.
 *
 * @param success       whether the provider call succeeded
 * @param output        tool output as JSON text; {@code null} on failure
 * @param errorMessage  failure detail; {@code null} on success
 * @param latencyMillis wall-clock duration of the call in milliseconds; never negative
 */
public record ToolResult(boolean success, String output, String errorMessage, long latencyMillis) {

  /**
   * Validates that {@code latencyMillis} is not negative.
   */
  public ToolResult {
    Validate.isNotNegative(latencyMillis, "ToolResult latencyMillis must not be negative");
  }

  /**
   * Builds a successful result.
   *
   * @param output        tool output as JSON text, or {@code null}
   * @param latencyMillis call duration in milliseconds
   *
   * @return a success result
   */
  public static ToolResult success(String output, long latencyMillis) {
    return new ToolResult(true, output, null, latencyMillis);
  }

  /**
   * Builds a failure result.
   *
   * @param errorMessage  failure detail
   * @param latencyMillis call duration in milliseconds
   *
   * @return a failure result
   */
  public static ToolResult failure(String errorMessage, long latencyMillis) {
    return new ToolResult(false, null, errorMessage, latencyMillis);
  }
}
