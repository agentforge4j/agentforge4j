package com.agentforge4j.core.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderPreferenceTest {

  @Test
  void accepts_null_model_as_default_model_signal() {
    ProviderPreference pref = new ProviderPreference("openai", null);

    assertThat(pref.provider()).isEqualTo("openai");
    assertThat(pref.model()).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "  ", "\t", "\n"})
  void rejects_blank_provider(String provider) {
    assertThatThrownBy(() -> new ProviderPreference(provider, "gpt-4o"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ProviderPreference provider must not be blank");
  }
}
