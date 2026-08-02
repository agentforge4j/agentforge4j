// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.RawProviderConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the {@code max-output-tokens} properties-path wiring: {@link GeminiConfigurationAdapter}
 * must translate the {@code agentforge4j.llm.gemini.max-output-tokens} property into the {@code max.output.tokens}
 * neutral option that {@link GeminiNeutralConfiguration#fromNeutral} reads, so setting the property through Spring
 * properties (or any other {@link RawProviderConfiguration} source) is not silently ignored.
 */
class GeminiConfigurationAdapterTest {

  private final GeminiConfigurationAdapter adapter = new GeminiConfigurationAdapter();

  @Test
  void mapsMaxOutputTokensPropertyToNeutralOption() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("api-key", "gm-test");
    values.put("default-model", "gemini-1.5-pro");
    values.put("base-url", "https://generativelanguage.googleapis.com");
    values.put("max-output-tokens", "512");
    RawProviderConfiguration raw = new RawProviderConfiguration("gemini", values::get, values.keySet());

    LlmClientConfiguration neutral = adapter.adapt(raw);

    assertThat(neutral.getOptions().integer("max.output.tokens")).contains(512);
  }

  @Test
  void omitsMaxOutputTokensOptionWhenPropertyAbsent() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("api-key", "gm-test");
    values.put("default-model", "gemini-1.5-pro");
    values.put("base-url", "https://generativelanguage.googleapis.com");
    RawProviderConfiguration raw = new RawProviderConfiguration("gemini", values::get, values.keySet());

    LlmClientConfiguration neutral = adapter.adapt(raw);

    assertThat(neutral.getOptions().integer("max.output.tokens")).isEmpty();
  }
}
