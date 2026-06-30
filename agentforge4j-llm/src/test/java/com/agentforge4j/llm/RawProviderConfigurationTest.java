// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused coverage of {@link RawProviderConfiguration}: duration/boolean/numeric parsing, presence-versus-blank
 * semantics, the secret-safe error contract, and the enumeration views. Exercised without framework infrastructure —
 * values are supplied through a plain {@link RawConfigurationSource} backed by a map, mirroring how the starter resolves
 * keys.
 */
class RawProviderConfigurationTest {

  private static RawProviderConfiguration config(Map<String, String> values) {
    return new RawProviderConfiguration("openai", values::get, values.keySet());
  }

  private static RawProviderConfiguration single(String key, String value) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(key, value);
    return config(values);
  }

  // ----- duration parsing -----

  @Test
  void parsesIso8601Duration() {
    assertThat(single("request-timeout", "PT45S").getDuration("request-timeout"))
        .contains(Duration.ofSeconds(45));
  }

  @Test
  void parsesShorthandDurations() {
    assertThat(single("request-timeout", "15s").getDuration("request-timeout")).contains(Duration.ofSeconds(15));
    assertThat(single("request-timeout", "2m").getDuration("request-timeout")).contains(Duration.ofMinutes(2));
    assertThat(single("request-timeout", "500ms").getDuration("request-timeout")).contains(Duration.ofMillis(500));
    assertThat(single("request-timeout", "1h").getDuration("request-timeout")).contains(Duration.ofHours(1));
    assertThat(single("request-timeout", "1d").getDuration("request-timeout")).contains(Duration.ofDays(1));
  }

  @Test
  void parsesUnitlessDurationAsMilliseconds() {
    assertThat(single("request-timeout", "5000").getDuration("request-timeout")).contains(Duration.ofMillis(5000));
  }

  @Test
  void rejectsMalformedDurationWithoutEchoingTheValue() {
    assertThatThrownBy(() -> single("request-timeout", "not-a-duration").getDuration("request-timeout"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("agentforge4j.llm.openai.request-timeout")
        .hasMessageNotContaining("not-a-duration");
  }

  @Test
  void rejectsOverflowingDurationAmount() {
    // Microsecond multiply (×1000) overflows a long; treated as a malformed duration, not an ArithmeticException leak.
    assertThatThrownBy(() -> single("request-timeout", "9223372036854775807us").getDuration("request-timeout"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("agentforge4j.llm.openai.request-timeout");

    // An amount that overflows long parsing is likewise a malformed duration, not a NumberFormatException leak.
    assertThatThrownBy(() -> single("request-timeout", "99999999999999999999s").getDuration("request-timeout"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageNotContaining("99999999999999999999");
  }

  @Test
  void treatsBlankDurationAsAbsent() {
    assertThat(single("request-timeout", "").getDuration("request-timeout")).isEmpty();
    assertThat(config(Map.of()).getDuration("request-timeout")).isEmpty();
  }

  // ----- boolean parsing -----

  @Test
  void parsesStrictBooleansCaseInsensitively() {
    assertThat(single("enabled", "true").getBoolean("enabled")).contains(Boolean.TRUE);
    assertThat(single("enabled", "TRUE").getBoolean("enabled")).contains(Boolean.TRUE);
    assertThat(single("enabled", "false").getBoolean("enabled")).contains(Boolean.FALSE);
    assertThat(single("enabled", "False").getBoolean("enabled")).contains(Boolean.FALSE);
  }

  @Test
  void rejectsNonStrictBooleanTokens() {
    for (String token : new String[] {"yes", "on", "1"}) {
      assertThatThrownBy(() -> single("enabled", token).getBoolean("enabled"))
          .as("token '%s' must be rejected", token)
          .isInstanceOf(LlmProviderConfigurationException.class)
          .hasMessageContaining("agentforge4j.llm.openai.enabled")
          .hasMessageNotContaining(token);
    }
  }

  @Test
  void treatsBlankBooleanAsAbsent() {
    assertThat(single("enabled", "   ").getBoolean("enabled")).isEmpty();
    assertThat(config(Map.of()).getBoolean("enabled")).isEmpty();
  }

  // ----- isTrue (enabled-style activation gate) -----

  @Test
  void isTrueOnlyForLiteralTrueCaseInsensitive() {
    assertThat(single("enabled", "true").isTrue("enabled")).isTrue();
    assertThat(single("enabled", "TRUE").isTrue("enabled")).isTrue();
    assertThat(single("enabled", "True").isTrue("enabled")).isTrue();
  }

  @Test
  void isTrueIsFalseForFalseAbsentOrBlank() {
    assertThat(single("enabled", "false").isTrue("enabled")).isFalse();
    assertThat(config(Map.of()).isTrue("enabled")).isFalse();
    assertThat(single("enabled", "   ").isTrue("enabled")).isFalse();
  }

  @Test
  void isTrueToleratesMalformedValuesAsFalseWithoutThrowing() {
    for (String token : new String[] {"yes", "on", "1", "maybe"}) {
      assertThatCode(() -> assertThat(single("enabled", token).isTrue("enabled"))
          .as("token '%s' must be tolerated as false", token)
          .isFalse())
          .as("token '%s' must not throw", token)
          .doesNotThrowAnyException();
    }
  }

  // ----- numeric parsing -----

  @Test
  void parsesIntegersAndDoubles() {
    assertThat(single("max-tokens", "256").getInt("max-tokens")).contains(256);
    assertThat(single("temperature", "0.7").getDouble("temperature")).contains(0.7d);
  }

  @Test
  void rejectsMalformedNumbersWithoutEchoingTheValue() {
    assertThatThrownBy(() -> single("max-tokens", "not-an-int").getInt("max-tokens"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("agentforge4j.llm.openai.max-tokens")
        .hasMessageNotContaining("not-an-int");

    assertThatThrownBy(() -> single("temperature", "not-a-double").getDouble("temperature"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("agentforge4j.llm.openai.temperature")
        .hasMessageNotContaining("not-a-double");
  }

  @Test
  void treatsBlankNumbersAsAbsent() {
    assertThat(single("max-tokens", "").getInt("max-tokens")).isEmpty();
    assertThat(single("temperature", " ").getDouble("temperature")).isEmpty();
  }

  // ----- presence handling -----

  @Test
  void getReturnsPresentBlankValue() {
    assertThat(single("api-key", "").get("api-key")).contains("");
    assertThat(config(Map.of()).get("api-key")).isEmpty();
  }

  @Test
  void requireTreatsBlankAsAbsentAndNamesTheKey() {
    assertThatThrownBy(() -> single("api-key", "  ").require("api-key"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("agentforge4j.llm.openai.api-key");

    assertThat(single("api-key", "sk-123").require("api-key")).isEqualTo("sk-123");
  }

  @Test
  void requireNeverEchoesTheConfiguredValue() {
    // A blank-but-present secret must not leak through the "not configured" message.
    assertThatThrownBy(() -> single("api-key", "   ").require("api-key"))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("agentforge4j.llm.openai.api-key");
  }

  // ----- enumeration views -----

  @Test
  void exposesProviderIdAndEnumerationViews() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("api-key", "k");
    values.put("default-model", "gpt-4o");
    RawProviderConfiguration raw = config(values);

    assertThat(raw.providerId()).isEqualTo("openai");
    assertThat(raw.isEmpty()).isFalse();
    assertThat(raw.keys()).containsExactlyInAnyOrder("api-key", "default-model");
    assertThat(raw.asMap()).containsEntry("api-key", "k").containsEntry("default-model", "gpt-4o");

    assertThat(config(Map.of()).isEmpty()).isTrue();
    assertThat(config(Map.of()).keys()).isEmpty();
    assertThat(config(Map.of()).asMap()).isEmpty();
  }
}
