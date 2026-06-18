// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmSecretTest {

  @Test
  void exposes_value_via_accessors() {
    LlmSecret secret = new LlmSecret("sk-abc123");

    assertThat(secret.value()).isEqualTo("sk-abc123");
  }

  @Test
  void toString_is_redacted_and_does_not_contain_the_value() {
    LlmSecret secret = new LlmSecret("sk-abc123");

    assertThat(secret.toString()).isEqualTo("LlmSecret[REDACTED]");
    assertThat(secret.toString()).doesNotContain("sk-abc123");
  }

  @Test
  void blank_value_is_rejected() {
    assertThatThrownBy(() -> new LlmSecret(" ")).isInstanceOf(IllegalArgumentException.class);
  }
}
