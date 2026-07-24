// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.util.time.DurationParser;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

/**
 * Shared, package-private parsing for the typed-value accessors of {@link RawProviderConfiguration} and
 * {@link MapLlmProviderOptions}. Both classes expose the same value vocabulary (integer, decimal, boolean, duration)
 * over an already-trimmed, non-blank candidate string; they differ only in how a parse failure is reported, so each
 * call site supplies its own {@link LlmProviderConfigurationException} factory. The factory receives the causing
 * {@link Throwable} when there is one, or {@code null} when a value is simply the wrong shape (for example an
 * unrecognised boolean token, or a duration that matches neither accepted grammar).
 *
 * <p>Duration parsing delegates to the shared {@link DurationParser} grammar — ISO-8601 (for example
 * {@code PT15S}) and a compact shorthand (for example {@code 15s}, {@code 2m}, {@code 500ms},
 * {@code 5000}) — so a duration-typed configuration value or provider option can be written in either
 * form, regardless of which accessor reads it, and stays in lockstep with the bootstrap
 * auto-discovery surface that shares the same grammar.
 */
final class TypedValueParser {

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
    try {
      return DurationParser.parse(value);
    } catch (IllegalArgumentException failure) {
      // DurationParser attaches the causing DateTimeParseException/numeric failure when one
      // exists and none for a value that matches neither grammar's shape — the same distinction
      // this method's errorFactory contract promises its callers.
      throw errorFactory.apply(failure.getCause());
    }
  }
}
