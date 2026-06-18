// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderOptionsTest {

  @Test
  void empty_has_no_keys_and_no_values() {
    LlmProviderOptions options = LlmProviderOptions.empty();

    assertThat(options.keys()).isEmpty();
    assertThat(options.string("anything")).isEmpty();
    assertThat(options.integer("anything")).isEmpty();
  }

  @Test
  void typed_accessors_parse_present_values() {
    LlmProviderOptions options = LlmProviderOptions.of("openai-compatible", Map.of(
        "auth.header.name", "Authorization",
        "request.timeout", "PT30S",
        "max.retries", "3",
        "streaming", "true"));

    assertThat(options.string("auth.header.name")).contains("Authorization");
    assertThat(options.duration("request.timeout")).contains(Duration.ofSeconds(30));
    assertThat(options.integer("max.retries")).contains(3);
    assertThat(options.bool("streaming")).contains(true);
    assertThat(options.keys()).contains("auth.header.name", "request.timeout");
  }

  @Test
  void string_preserves_significant_whitespace() {
    // An auth header prefix such as "Bearer " carries a meaningful trailing space; string() must not
    // trim it (only the typed parsers trim).
    LlmProviderOptions options = LlmProviderOptions.of("openai-compatible",
        Map.of("auth.header.prefix", "Bearer "));

    assertThat(options.string("auth.header.prefix")).contains("Bearer ");
  }

  @Test
  void blank_value_is_treated_as_absent() {
    LlmProviderOptions options = LlmProviderOptions.of("openai", Map.of("base.url", "   "));

    assertThat(options.string("base.url")).isEmpty();
  }

  @Test
  void secret_value_parses_to_a_reference() {
    LlmProviderOptions options = LlmProviderOptions.of("openai",
        Map.of("api.key", "${env:OPENAI_API_KEY}"));

    assertThat(options.secret("api.key")).hasValueSatisfying(ref -> {
      assertThat(ref.isLiteral()).isFalse();
      assertThat(ref.scheme()).isEqualTo("env");
      assertThat(ref.key()).isEqualTo("OPENAI_API_KEY");
    });
  }

  @Test
  void invalid_integer_throws_naming_provider_and_key_but_not_value() {
    LlmProviderOptions options = LlmProviderOptions.of("openai", Map.of("max.retries", "many"));

    assertThatThrownBy(() -> options.integer("max.retries"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("openai")
        .hasMessageContaining("max.retries")
        .hasMessageNotContaining("many");
  }

  @Test
  void invalid_boolean_throws() {
    LlmProviderOptions options = LlmProviderOptions.of("openai", Map.of("streaming", "yes"));

    assertThatThrownBy(() -> options.bool("streaming"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("streaming");
  }

  @Test
  void invalid_duration_throws() {
    LlmProviderOptions options = LlmProviderOptions.of("openai", Map.of("request.timeout", "30s"));

    assertThatThrownBy(() -> options.duration("request.timeout"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("request.timeout");
  }
}
