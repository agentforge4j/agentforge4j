// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmSecretReferenceTest {

  @Test
  void parse_env_reference() {
    LlmSecretReference ref = LlmSecretReference.parse("${env:OPENAI_API_KEY}");

    assertThat(ref.isLiteral()).isFalse();
    assertThat(ref.scheme()).isEqualTo("env");
    assertThat(ref.key()).isEqualTo("OPENAI_API_KEY");
  }

  @Test
  void parse_sysprop_reference_trims_whitespace() {
    LlmSecretReference ref = LlmSecretReference.parse("  ${sysprop:openai.key}  ");

    assertThat(ref.isLiteral()).isFalse();
    assertThat(ref.scheme()).isEqualTo("sysprop");
    assertThat(ref.key()).isEqualTo("openai.key");
  }

  @Test
  void parse_plain_value_is_literal() {
    LlmSecretReference ref = LlmSecretReference.parse("sk-abc123");

    assertThat(ref.isLiteral()).isTrue();
    assertThat(ref.literalValue()).isEqualTo("sk-abc123");
  }

  @Test
  void unknown_scheme_is_treated_as_literal() {
    LlmSecretReference ref = LlmSecretReference.parse("${vault:secret/key}");

    assertThat(ref.isLiteral()).isTrue();
    assertThat(ref.literalValue()).isEqualTo("${vault:secret/key}");
  }

  @Test
  void literal_toString_is_redacted() {
    LlmSecretReference ref = LlmSecretReference.literal("sk-abc123");

    assertThat(ref.toString()).isEqualTo("LlmSecretReference[REDACTED]");
    assertThat(ref.toString()).doesNotContain("sk-abc123");
  }

  @Test
  void indirect_toString_shows_scheme_and_key_only() {
    LlmSecretReference ref = LlmSecretReference.reference("env", "OPENAI_API_KEY");

    assertThat(ref.toString()).isEqualTo("LlmSecretReference[env:OPENAI_API_KEY]");
  }

  @Test
  void accessors_guard_against_wrong_kind() {
    LlmSecretReference literal = LlmSecretReference.literal("v");
    LlmSecretReference indirect = LlmSecretReference.reference("env", "K");

    assertThatThrownBy(literal::scheme).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(literal::key).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(indirect::literalValue).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void equality_is_by_value() {
    assertThat(LlmSecretReference.reference("env", "K"))
        .isEqualTo(LlmSecretReference.reference("env", "K"))
        .hasSameHashCodeAs(LlmSecretReference.reference("env", "K"));
    assertThat(LlmSecretReference.literal("v")).isEqualTo(LlmSecretReference.literal("v"));
    assertThat(LlmSecretReference.literal("a")).isNotEqualTo(LlmSecretReference.literal("b"));
  }

  @Test
  void blank_inputs_are_rejected() {
    assertThatThrownBy(() -> LlmSecretReference.literal(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LlmSecretReference.reference("env", " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LlmSecretReference.parse(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
