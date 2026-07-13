// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.util.Validate;
import java.util.Map;

/**
 * Shared, fail-closed coercion of a declared {@link ContextValue} into a validated numeric
 * primitive, tolerant of both a {@link NumberContextValue} (agent-produced sizing figures) and a
 * {@link StringContextValue} (submitted {@code INPUT} answers, which always arrive as strings).
 * Factored out of {@code WorkflowExecutionEstimateAggregator} so this parsing is written once and
 * reusable by any aggregator in this package, rather than each reinventing it — the pattern this
 * class captures is exactly what a host-side helper independently duplicated before being deleted
 * when the shipped example moved to in-workflow aggregation.
 */
final class ContextValueNumbers {

  private ContextValueNumbers() {
  }

  /**
   * Looks up {@code key}, failing closed if absent.
   *
   * @param values declared context values
   * @param key    the key to look up; must be present
   *
   * @return the present value
   */
  static ContextValue require(Map<String, ContextValue> values, String key) {
    ContextValue value = values.get(key);
    Validate.notNull(value, "Missing declared context value for key '%s'".formatted(key));
    return value;
  }

  /**
   * Reads {@code key} as a non-negative, exact-integer {@code long}.
   *
   * @param values declared context values
   * @param key    the key to read; must be present
   *
   * @return the parsed, non-negative value
   *
   * @throws IllegalArgumentException if the value is missing, not numeric/a numeric string,
   *                                   fractional, or negative
   */
  static long asLong(Map<String, ContextValue> values, String key) {
    long parsed = parseLong(values, key);
    if (parsed < 0) {
      throw new IllegalArgumentException(
          "Context value for key '%s' must not be negative but was %d".formatted(key, parsed));
    }
    return parsed;
  }

  /**
   * Reads {@code key} as a non-negative, exact-integer {@code int}.
   *
   * @param values declared context values
   * @param key    the key to read; must be present
   *
   * @return the parsed, non-negative value
   *
   * @throws IllegalArgumentException if the value is missing, not numeric/a numeric string,
   *                                   fractional, negative, or does not fit in an {@code int}
   */
  static int asInt(Map<String, ContextValue> values, String key) {
    long raw = asLong(values, key);
    if (raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "Context value for key '%s' must fit in a 32-bit integer but was %d".formatted(key, raw));
    }
    return (int) raw;
  }

  private static long parseLong(Map<String, ContextValue> values, String key) {
    ContextValue value = require(values, key);
    if (value instanceof NumberContextValue number) {
      double raw = number.value().doubleValue();
      if (Double.isNaN(raw) || Double.isInfinite(raw) || raw != Math.rint(raw)) {
        throw new IllegalArgumentException(
            "Context value for key '%s' must be an exact integer but was %s"
                .formatted(key, number.value()));
      }
      return number.value().longValue();
    }
    if (value instanceof StringContextValue string) {
      try {
        return Long.parseLong(string.value());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Context value for key '%s' is not a valid integer: '%s'".formatted(key, string.value()), e);
      }
    }
    throw new IllegalArgumentException(
        "Context value for key '%s' must be numeric or a numeric string but was %s"
            .formatted(key, value.getClass().getSimpleName()));
  }
}
