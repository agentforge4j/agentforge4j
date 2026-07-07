// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared, package-private parsing for the typed-value accessors of {@link RawProviderConfiguration} and
 * {@link MapLlmProviderOptions}. Both classes expose the same value vocabulary (integer, decimal, boolean, duration)
 * over an already-trimmed, non-blank candidate string; they differ only in how a parse failure is reported, so each
 * call site supplies its own {@link LlmProviderConfigurationException} factory. The factory receives the causing
 * {@link Throwable} when there is one, or {@code null} when a value is simply the wrong shape (for example an
 * unrecognised boolean token, or a duration that matches neither accepted grammar).
 *
 * <p>Duration parsing accepts both ISO-8601 (for example {@code PT15S}) and a compact shorthand — an amount plus an
 * optional {@code ns}/{@code us}/{@code ms}/{@code s}/{@code m}/{@code h}/{@code d} unit suffix, defaulting to
 * milliseconds (for example {@code 15s}, {@code 2m}, {@code 500ms}, {@code 5000}) — so a duration-typed
 * configuration value or provider option can be written in either form, regardless of which accessor reads it.
 */
final class TypedValueParser {

  // Compact duration shorthand: an amount plus an optional unit suffix (ns/us/ms/s/m/h/d), defaulting to
  // milliseconds -- for example "15s", "2m", "500ms". Accepted alongside ISO-8601 so a duration value can be
  // written in either form.
  private static final Pattern COMPACT_DURATION = Pattern.compile("^([+-]?\\d+)\\s*([a-zA-Z]{0,2})$");

  private TypedValueParser() {
  }

  /**
   * Parses a trimmed, non-blank candidate value as an integer.
   *
   * @param value        the trimmed, non-blank candidate value
   * @param errorFactory builds the exception to throw on failure, given the causing {@link NumberFormatException}
   *
   * @return the parsed value
   */
  static int parseInt(String value, Function<Throwable, LlmProviderConfigurationException> errorFactory) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException cause) {
      throw errorFactory.apply(cause);
    }
  }

  /**
   * Parses a trimmed, non-blank candidate value as a decimal number.
   *
   * @param value        the trimmed, non-blank candidate value
   * @param errorFactory builds the exception to throw on failure, given the causing {@link NumberFormatException}
   *
   * @return the parsed value
   */
  static double parseDecimal(String value, Function<Throwable, LlmProviderConfigurationException> errorFactory) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException cause) {
      throw errorFactory.apply(cause);
    }
  }

  /**
   * Parses a trimmed, non-blank candidate value as a strict boolean ({@code true}/{@code false}, case-insensitive).
   *
   * @param value        the trimmed, non-blank candidate value
   * @param errorFactory builds the exception to throw on failure; invoked with {@code null} since there is no
   *                     causing exception for an unrecognised token
   *
   * @return the parsed value
   */
  static boolean parseBool(String value, Function<Throwable, LlmProviderConfigurationException> errorFactory) {
    if (value.equalsIgnoreCase("true")) {
      return true;
    }
    if (value.equalsIgnoreCase("false")) {
      return false;
    }
    throw errorFactory.apply(null);
  }

  /**
   * Parses a trimmed, non-blank candidate value as a duration, accepting either ISO-8601 (for example
   * {@code PT15S}) or the compact shorthand (for example {@code 15s}, {@code 2m}, {@code 500ms}, {@code 5000}).
   *
   * @param value        the trimmed, non-blank candidate value
   * @param errorFactory builds the exception to throw on failure; invoked with the causing exception when there is
   *                     one ({@link DateTimeParseException} for a malformed ISO-8601 form), or {@code null} for a
   *                     malformed shorthand form
   *
   * @return the parsed value
   */
  static Duration parseDuration(String value, Function<Throwable, LlmProviderConfigurationException> errorFactory) {
    String lower = value.toLowerCase(Locale.ROOT);
    if (lower.startsWith("p") || lower.startsWith("+p") || lower.startsWith("-p")) {
      try {
        return Duration.parse(value);
      } catch (DateTimeParseException cause) {
        throw errorFactory.apply(cause);
      }
    }
    Matcher matcher = COMPACT_DURATION.matcher(value);
    if (!matcher.matches()) {
      throw errorFactory.apply(null);
    }
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
        default -> throw errorFactory.apply(null);
      };
    } catch (ArithmeticException | NumberFormatException cause) {
      // An out-of-range amount (overflow on parse or on the microsecond multiply) is a malformed
      // duration, not an ArithmeticException/NumberFormatException leak.
      throw errorFactory.apply(cause);
    }
  }
}
