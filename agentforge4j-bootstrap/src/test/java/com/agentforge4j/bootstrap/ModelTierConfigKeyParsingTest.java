// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers model-tier override key parsing (the private {@code Builder#getProviderEndIndex}), driven
 * through the public build path. {@code agentforge4j.llm.model-tiers.<provider>.<tier>=<model>}
 * entries are parsed during {@link AgentForge4jBootstrap.Builder#build()} when no explicit
 * {@code ModelTierResolver} is configured.
 *
 * <p>The split point is the <em>final</em> dot of the {@code <provider>.<tier>} remainder, so the
 * provider may itself contain dots or dashes while the tier is always the trailing segment. A key
 * that lacks a usable {@code provider.tier} split — no dot, an empty provider, or an empty tier —
 * is rejected with an {@link IllegalStateException} naming the offending key. A wrong split would
 * surface downstream as an invalid tier, so a clean build is itself evidence the split was correct.
 */
class ModelTierConfigKeyParsingTest {

  private static final String PREFIX = "agentforge4j.llm.model-tiers.";

  private final Map<String, String> originalValues = new HashMap<>();

  @AfterEach
  void restoreProperties() {
    for (Map.Entry<String, String> entry : originalValues.entrySet()) {
      String key = entry.getKey();
      String previous = entry.getValue();
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
    originalValues.clear();
  }

  // --- valid keys: provider/tier split at the final dot -----------------------------------------

  @Test
  void parsesSimpleProviderAndTier() {
    setProperty(PREFIX + "openai.standard", "gpt-some-model");

    assertThat(AgentForge4jBootstrap.defaults().build()).isNotNull();
  }

  @Test
  void acceptsProviderNameContainingDashes() {
    setProperty(PREFIX + "azure-openai.powerful", "azure-some-model");

    assertThat(AgentForge4jBootstrap.defaults().build()).isNotNull();
  }

  @Test
  void splitsOnTheFinalDotSoTheProviderMayContainDots() {
    // Remainder "openai.eu.lite": only last-dot splitting leaves a valid tier ("lite"); a first-dot
    // split would yield tier "eu.lite" and fail tier parsing. A clean build proves the final dot.
    setProperty(PREFIX + "openai.eu.lite", "eu-some-model");

    assertThat(AgentForge4jBootstrap.defaults().build()).isNotNull();
  }

  // --- malformed keys: rejected by getProviderEndIndex ------------------------------------------

  @Test
  void rejectsKeyWithNoTierSeparator() {
    setProperty(PREFIX + "openai", "gpt-some-model");

    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid model tier config key")
        .hasMessageContaining(PREFIX + "openai");
  }

  @Test
  void rejectsKeyWithEmptyProviderSegment() {
    setProperty(PREFIX + ".standard", "gpt-some-model");

    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid model tier config key");
  }

  @Test
  void rejectsKeyWithEmptyTierSegment() {
    setProperty(PREFIX + "openai.", "gpt-some-model");

    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid model tier config key");
  }

  private void setProperty(String key, String value) {
    originalValues.putIfAbsent(key, System.getProperty(key));
    System.setProperty(key, value);
  }
}
