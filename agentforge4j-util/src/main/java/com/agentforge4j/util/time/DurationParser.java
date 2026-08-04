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
 * {@code ns}/{@code us}/{@code ms}/{@code s}/{@code m}/{@code h}/{@code d} unit suffix. A unitless
 * amount is interpreted as milliseconds, so {@code 5000} means five seconds (other examples:
 * {@code 15s}, {@code 2m}, {@code 500ms}).
 *
 * <p>This parser centralises the grammar used by three configuration surfaces, so a duration-typed
 * value written for any of them accepts the same forms:
 *
 * <ul>
 *   <li>raw provider configuration ({@code RawProviderConfiguration});</li>
 *   <li>map-based provider options ({@code LlmProviderOptions});</li>
 *   <li>bootstrap LLM {@code connect.timeout} auto-discovery.</li>
 * </ul>
 *
 * <p>It is not a project-wide duration grammar: duration-typed settings on other surfaces may
 * define their own and are not required to use this one — MCP integration request timeouts, for
 * example, accept ISO-8601 only.
 *
 * <p>Callers that report failures through their own exception type catch the
 * {@link IllegalArgumentException} thrown here and translate it; the causing
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
   * Parses a candidate value as a duration, accepting either ISO-8601 (for example {@code PT15S})
   * or the compact shorthand (for example {@code 15s}, {@code 2m}, {@code 500ms}, {@code 5000}).
   *
   * <p>Surrounding whitespace is trimmed before parsing, so {@code " 15s "} parses exactly as
   * {@code "15s"} does — callers need not trim first. Whitespace <em>between</em> the amount and
   * its unit is part of the shorthand grammar and is also accepted ({@code "15 s"}); whitespace
   * anywhere else leaves the value matching neither grammar.
   *
   * @param value the candidate value; must be non-null and non-blank, and is trimmed before parsing
   *
   * @return the parsed duration
   *
   * @throws IllegalArgumentException when {@code value} is {@code null} or blank, or when the
   *                                  trimmed value matches neither grammar; the causing
   *                                  {@link DateTimeParseException} or numeric failure is attached
   *                                  when one exists, and absent otherwise
   */
  public static Duration parse(String value) {
    Validate.notBlank(value, "value must not be blank");
    String candidate = value.trim();
    String lower = candidate.toLowerCase(Locale.ROOT);
    if (lower.startsWith("p") || lower.startsWith("+p") || lower.startsWith("-p")) {
      try {
        return Duration.parse(candidate);
      } catch (DateTimeParseException cause) {
        throw new IllegalArgumentException(malformedMessage(candidate), cause);
      }
    }
    Matcher matcher = COMPACT_DURATION.matcher(candidate);
    Validate.isTrue(matcher.matches(),
        () -> new IllegalArgumentException(malformedMessage(candidate)));
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
        default -> throw new IllegalArgumentException(malformedMessage(candidate));
      };
    } catch (ArithmeticException | NumberFormatException cause) {
      // An out-of-range amount (overflow on parse or on the microsecond multiply) is a malformed
      // duration, not an ArithmeticException/NumberFormatException leak.
      throw new IllegalArgumentException(malformedMessage(candidate), cause);
    }
  }

  private static String malformedMessage(String value) {
    return "'%s' is not a valid duration (ISO-8601 like PT30S, or shorthand like 30s, 2m, 500ms)"
        .formatted(value);
  }
}
