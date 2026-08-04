// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LlmHttpErrorBodyTruncate}, shared by every {@code agentforge4j-llm-*}
 * provider module to bound how much of a raw response/error body is embedded in exception
 * messages or ERROR-level logs.
 * <p>
 * The bound is asserted against the literal {@code 500}, not against
 * {@link LlmHttpErrorBodyTruncate#DEFAULT_MAX_CHARS}, so that silently widening the constant fails
 * here rather than shipping.
 */
class LlmHttpErrorBodyTruncateTest {

  private static final int EXPECTED_BOUND = 500;

  @Test
  void default_max_chars_is_five_hundred() {
    assertThat(LlmHttpErrorBodyTruncate.DEFAULT_MAX_CHARS).isEqualTo(EXPECTED_BOUND);
  }

  @Test
  void returns_body_unchanged_when_shorter_than_the_bound() {
    assertThat(LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage("short body"))
        .isEqualTo("short body");
  }

  @Test
  void returns_body_unchanged_at_exactly_the_bound() {
    String body = "X".repeat(EXPECTED_BOUND);

    String truncated = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body);

    assertThat(truncated).hasSize(EXPECTED_BOUND);
    assertThat(truncated).isEqualTo(body);
  }

  @Test
  void truncates_to_the_bound_one_character_over() {
    String body = "X".repeat(EXPECTED_BOUND) + "Z";

    String truncated = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body);

    assertThat(truncated).hasSize(EXPECTED_BOUND);
    assertThat(truncated).doesNotContain("Z");
  }

  @Test
  void truncates_a_body_far_longer_than_the_bound() {
    String body = "X".repeat(10_000);

    String truncated = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body);

    assertThat(truncated).hasSize(EXPECTED_BOUND);
    assertThat(truncated).isEqualTo("X".repeat(EXPECTED_BOUND));
  }

  @Test
  void treats_null_body_as_empty() {
    assertThat(LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(null)).isEmpty();
  }

  @Test
  void returns_empty_string_for_empty_body() {
    assertThat(LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage("")).isEmpty();
  }

  @Test
  void counts_char_units_not_code_points_for_multibyte_bodies() {
    // Each emoji is one code point but two chars, so 400 of them are 800 chars: the bound cuts
    // at 500 chars, that is, after 250 whole emoji.
    String body = "😀".repeat(400);

    String truncated = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body);

    assertThat(truncated).hasSize(EXPECTED_BOUND);
    assertThat(truncated.codePointCount(0, truncated.length())).isEqualTo(250);
    assertThat(truncated).isEqualTo("😀".repeat(250));
  }

  @Test
  void may_end_in_an_unpaired_surrogate_when_the_bound_splits_a_pair() {
    // One leading 'a' shifts every pair by one char, so the 500-char cut lands mid-pair.
    String body = "a" + "😀".repeat(400);

    String truncated = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body);

    assertThat(truncated).hasSize(EXPECTED_BOUND);
    assertThat(Character.isHighSurrogate(truncated.charAt(EXPECTED_BOUND - 1))).isTrue();
  }

  @Test
  void preserves_multibyte_bodies_shorter_than_the_bound() {
    String body = "héllo wörld — ünïcode ✓";

    assertThat(LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body)).isEqualTo(body);
  }

  @Test
  void does_not_collapse_or_strip_newlines() {
    String body = "line one\nline two\r\nline three\n";

    assertThat(LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body)).isEqualTo(body);
  }

  @Test
  void truncates_across_newlines_without_reformatting() {
    String body = "0123456789\n".repeat(100);

    String truncated = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body);

    assertThat(truncated).hasSize(EXPECTED_BOUND);
    assertThat(truncated).isEqualTo(body.substring(0, EXPECTED_BOUND));
    assertThat(truncated.chars().filter(c -> c == '\n').count())
        .isEqualTo(EXPECTED_BOUND / "0123456789\n".length());
  }
}
