// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.ModelTier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped provider→tier→model defaults as one complete matrix rather than a sample.
 *
 * <p>The mappings are hand-written data with no compile-time check that a model string landed on the
 * tier it was meant for, so every one of the shipped cells is stated here explicitly. Because the
 * expectation is a whole map compared as a whole map, this also fails when a provider or a tier is
 * added or removed without the matrix being updated — the two directions a per-cell spot check
 * cannot see.
 */
class ShippedModelTierDefaultsTest {

  /**
   * The complete expected matrix: every built-in provider, every declared {@link ModelTier}.
   *
   * <p>{@code PREMIUM} ships equal to {@code POWERFUL} for every provider today — no built-in
   * provider is configured with a distinct higher-capability model. That is a supported mapping, not
   * a placeholder, and it is asserted rather than assumed so a future divergence is a deliberate
   * edit here.
   */
  private static Map<String, Map<ModelTier, String>> expectedMatrix() {
    Map<String, Map<ModelTier, String>> expected = new LinkedHashMap<>();
    expected.put("openai", Map.of(
        ModelTier.LITE, "gpt-5.4-nano",
        ModelTier.STANDARD, "gpt-5.4-mini",
        ModelTier.POWERFUL, "gpt-5.5",
        ModelTier.PREMIUM, "gpt-5.5"));
    expected.put("claude", Map.of(
        ModelTier.LITE, "claude-haiku-4-5-20251001",
        ModelTier.STANDARD, "claude-sonnet-4-6",
        ModelTier.POWERFUL, "claude-opus-4-8",
        ModelTier.PREMIUM, "claude-opus-4-8"));
    expected.put("gemini", Map.of(
        ModelTier.LITE, "gemini-3.1-flash-lite",
        ModelTier.STANDARD, "gemini-3.5-flash",
        ModelTier.POWERFUL, "gemini-3.1-pro",
        ModelTier.PREMIUM, "gemini-3.1-pro"));
    expected.put("mistral", Map.of(
        ModelTier.LITE, "mistral-small-2603",
        ModelTier.STANDARD, "mistral-medium-3-5",
        ModelTier.POWERFUL, "mistral-large-2512",
        ModelTier.PREMIUM, "mistral-large-2512"));
    expected.put("bedrock", Map.of(
        ModelTier.LITE, "anthropic.claude-haiku-4-5-20251001-v1:0",
        ModelTier.STANDARD, "anthropic.claude-sonnet-4-6",
        ModelTier.POWERFUL, "anthropic.claude-opus-4-8",
        ModelTier.PREMIUM, "anthropic.claude-opus-4-8"));
    expected.put("azure-openai", Map.of(
        ModelTier.LITE, "gpt-5.4-nano",
        ModelTier.STANDARD, "gpt-5.4-mini",
        ModelTier.POWERFUL, "gpt-5.5",
        ModelTier.PREMIUM, "gpt-5.5"));
    expected.put("ollama", Map.of(
        ModelTier.LITE, "qwen3:4b",
        ModelTier.STANDARD, "qwen3:14b",
        ModelTier.POWERFUL, "qwen3:32b",
        ModelTier.PREMIUM, "qwen3:32b"));
    expected.put("vllm", Map.of(
        ModelTier.LITE, "Qwen/Qwen3-4B",
        ModelTier.STANDARD, "Qwen/Qwen3-14B",
        ModelTier.POWERFUL, "Qwen/Qwen3-32B",
        ModelTier.PREMIUM, "Qwen/Qwen3-32B"));
    expected.put("openai-compatible", Map.of(
        ModelTier.LITE, "gpt-5.4-nano",
        ModelTier.STANDARD, "gpt-5.4-mini",
        ModelTier.POWERFUL, "gpt-5.5",
        ModelTier.PREMIUM, "gpt-5.5"));
    return expected;
  }

  @Test
  void shipsTheCompleteExpectedProviderByTierMatrix() {
    assertThat(ShippedModelTierDefaults.asMap())
        .containsExactlyInAnyOrderEntriesOf(expectedMatrix());
  }

  @Test
  void matrixCoversEveryDeclaredTierForEveryShippedProvider() {
    Map<String, Map<ModelTier, String>> shipped = ShippedModelTierDefaults.asMap();

    assertThat(shipped).isNotEmpty();
    for (Map.Entry<String, Map<ModelTier, String>> provider : shipped.entrySet()) {
      assertThat(provider.getValue().keySet())
          .as("provider '%s' must map every declared tier", provider.getKey())
          .containsExactlyInAnyOrder(ModelTier.values());
    }
  }

  @Test
  void returnsAFreshMutableCopyOnEveryCall() {
    Map<String, Map<ModelTier, String>> first = ShippedModelTierDefaults.asMap();
    first.remove("openai");
    first.get("claude").put(ModelTier.PREMIUM, "mutated");

    Map<String, Map<ModelTier, String>> second = ShippedModelTierDefaults.asMap();

    assertThat(second).containsKey("openai");
    assertThat(second.get("claude")).containsEntry(ModelTier.PREMIUM, "claude-opus-4-8");
  }
}
