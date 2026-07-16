// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.time;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationParserTest {

  @Test
  void parsesIso8601() {
    assertThat(DurationParser.parse("PT30S")).isEqualTo(Duration.ofSeconds(30));
    assertThat(DurationParser.parse("pt1m")).isEqualTo(Duration.ofMinutes(1));
    assertThat(DurationParser.parse("-PT2S")).isEqualTo(Duration.ofSeconds(-2));
  }

  @ParameterizedTest
  @CsvSource({
      "5000, 5000",
      "500ms, 500",
      "15s, 15000",
      "2m, 120000",
      "1h, 3600000",
      "1d, 86400000",
  })
  void parsesCompactShorthandToMillis(String value, long expectedMillis) {
    assertThat(DurationParser.parse(value)).isEqualTo(Duration.ofMillis(expectedMillis));
  }

  @Test
  void parsesSubMillisecondUnits() {
    assertThat(DurationParser.parse("250ns")).isEqualTo(Duration.ofNanos(250));
    assertThat(DurationParser.parse("250us")).isEqualTo(Duration.ofNanos(250_000));
  }

  @Test
  void acceptsSignedShorthandAndWhitespaceBeforeTheUnit() {
    assertThat(DurationParser.parse("-15s")).isEqualTo(Duration.ofSeconds(-15));
    assertThat(DurationParser.parse("+15s")).isEqualTo(Duration.ofSeconds(15));
    assertThat(DurationParser.parse("15 s")).isEqualTo(Duration.ofSeconds(15));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "30x", "1.5s", "s", "30 seconds"})
  void rejectsValuesMatchingNeitherGrammarWithoutACause(String value) {
    assertThatThrownBy(() -> DurationParser.parse(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(value)
        .hasNoCause();
  }

  @Test
  void rejectsMalformedIso8601WithTheParseCause() {
    assertThatThrownBy(() -> DurationParser.parse("PT30X"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasCauseInstanceOf(DateTimeParseException.class);
  }

  @Test
  void rejectsOutOfRangeAmountsAsMalformedNotAsArithmeticLeaks() {
    assertThatThrownBy(() -> DurationParser.parse("9223372036854775808"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasCauseInstanceOf(NumberFormatException.class);
    assertThatThrownBy(() -> DurationParser.parse("9223372036854775807us"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasCauseInstanceOf(ArithmeticException.class);
  }

  @Test
  void rejectsBlankInput() {
    assertThatThrownBy(() -> DurationParser.parse(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
