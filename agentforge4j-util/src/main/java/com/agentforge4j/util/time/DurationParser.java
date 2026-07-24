// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.time;

import com.agentforge4j.util.Validate;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the shared duration grammar used by AgentForge4j configuration surfaces: ISO-8601 (for
 * example {@code PT15S}) and a compact shorthand — an amount plus an optional
 * {@code ns}/{@code us}/{@code ms}/{@code s}/{@code m}/{@code h}/{@code d} unit suffix, defaulting
 * to milliseconds (for example {@code 15s}, {@code 2m}, {@code 500ms}, {@code 5000}).
 *
 * <p>Single home for the grammar so every duration-typed configuration value accepts the same
 * forms regardless of which surface reads it (provider options, raw provider configuration, or
 * bootstrap auto-discovery). Callers that report failures through their own exception type catch
 * the {@link IllegalArgumentException} thrown here and translate it; the causing
 * {@link DateTimeParseException} (malformed ISO-8601) or numeric failure is attached as the cause
 * when one exists, and absent for a value that matches neither grammar's shape.
 */
public final class DurationParser {

  // Compact duration shorthand: an amount plus an optional unit suffix (ns/us/ms/s/m/h/d),
  // defaulting to milliseconds -- for example "15s", "2m", "500ms". Accepted alongside ISO-8601 so
  // a duration value can be written in either form.
  private static final Pattern COMPACT_DURATION = Pattern.compile("^([+-]?\\d+)\\s*([a-zA-Z]{0,2})$");

  private DurationParser() {
  }

  /**
   * Parses a trimmed, non-blank candidate value as a duration, accepting either ISO-8601 (for
   * example {@code PT15S}) or the compact shorthand (for example {@code 15s}, {@code 2m},
   * {@code 500ms}, {@code 5000}).
   *
   * @param value the trimmed, non-blank candidate value
   *
   * @return the parsed duration
   *
   * @throws IllegalArgumentException when the value matches neither grammar; the causing
   *                                  {@link DateTimeParseException} or numeric failure is attached
   *                                  when one exists
   */
  public static Duration parse(String value) {
    Validate.notBlank(value, "value must not be blank");
    String lower = value.toLowerCase(Locale.ROOT);
    if (lower.startsWith("p") || lower.startsWith("+p") || lower.startsWith("-p")) {
      try {
        return Duration.parse(value);
      } catch (DateTimeParseException cause) {
        throw new IllegalArgumentException(malformedMessage(value), cause);
      }
    }
    Matcher matcher = COMPACT_DURATION.matcher(value);
    Validate.isTrue(matcher.matches(),
        () -> new IllegalArgumentException(malformedMessage(value)));
    try {
      long amount = Long.parseLong(matcher.group(1));
      return switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
        case "", "ms" -> Duration.ofMillis(amount);
        case "ns" -> Duration.ofNanos(amount);
        case "us" -> Duration.ofNanos(Math.multiplyExact(amount, 1_000L));
        case "s" -> Duration.ofSeconds(amount);
        case "m" -> Duration.ofMinutes(amount);
        case "h" -> Duration.ofHours(amount);
        case "d" -> Duration.ofDays(amount);
        default -> throw new IllegalArgumentException(malformedMessage(value));
      };
    } catch (ArithmeticException | NumberFormatException cause) {
      // An out-of-range amount (overflow on parse or on the microsecond multiply) is a malformed
      // duration, not an ArithmeticException/NumberFormatException leak.
      throw new IllegalArgumentException(malformedMessage(value), cause);
    }
  }

  private static String malformedMessage(String value) {
    return "'%s' is not a valid duration (ISO-8601 like PT30S, or shorthand like 30s, 2m, 500ms)"
        .formatted(value);
  }
}
