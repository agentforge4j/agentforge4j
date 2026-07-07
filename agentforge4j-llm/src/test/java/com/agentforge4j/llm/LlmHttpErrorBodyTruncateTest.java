// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LlmHttpErrorBodyTruncate}, shared by every {@code agentforge4j-llm-*}
 * provider module to bound how much of a raw response/error body is embedded in exception
 * messages or ERROR-level logs.
 */
class LlmHttpErrorBodyTruncateTest {

  @Test
  void truncateForEmbeddedMessage_returns_body_unchanged_when_shorter_than_max() {
    assertThat(LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage("short body", 500))
        .isEqualTo("short body");
  }

  @Test
  void truncateForEmbeddedMessage_truncates_body_longer_than_max() {
    String body = "X".repeat(1_000);

    String truncated = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body, 500);

    assertThat(truncated).hasSize(500);
    assertThat(truncated).isEqualTo("X".repeat(500));
  }

  @Test
  void truncateForEmbeddedMessage_treats_null_body_as_empty() {
    assertThat(LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(null, 500)).isEmpty();
  }

  @Test
  void truncateForEmbeddedMessage_returns_empty_string_for_non_positive_max_chars() {
    assertThat(LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage("some body", 0)).isEmpty();
    assertThat(LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage("some body", -1)).isEmpty();
  }

  @Test
  void single_arg_overload_uses_default_max_chars() {
    String body = "Y".repeat(1_000);

    String truncated = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(body);

    assertThat(truncated).hasSize(LlmHttpErrorBodyTruncate.DEFAULT_MAX_CHARS);
    assertThat(truncated).isEqualTo("Y".repeat(LlmHttpErrorBodyTruncate.DEFAULT_MAX_CHARS));
  }
}
