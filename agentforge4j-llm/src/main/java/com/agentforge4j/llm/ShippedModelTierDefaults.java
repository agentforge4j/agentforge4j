// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.ModelTier;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The shipped OSS default mapping of {@link ModelTier} to a concrete, versioned model string for
 * each of the nine built-in providers. These are the out-of-the-box defaults; operators
 * may override any provider/tier entry, and an embedding application may further override them.
 *
 * <p>Local providers ({@code ollama}, {@code vllm}) default to Qwen models; {@code azure-openai}
 * uses OpenAI model names (the Azure deployment name is configured separately); {@code bedrock}
 * uses Anthropic Claude model identifiers.
 *
 * <p>Each provider maps {@link ModelTier#PREMIUM} to its strongest available model — the same string
 * as {@link ModelTier#POWERFUL} where no distinct higher-capability model is configured. Mapping two
 * tiers to one model is a supported configuration; operators may override the PREMIUM entry to a
 * distinct model as with any other tier.
 */
public final class ShippedModelTierDefaults {

  private ShippedModelTierDefaults() {
  }

  /**
   * Returns a fresh, mutable provider→tier→model map of the shipped defaults. The returned map is a
   * defensive copy; callers may merge overrides into it without affecting the shipped baseline.
   *
   * @return the shipped default tier mappings keyed by lowercase provider name; never {@code null}
   */
  public static Map<String, Map<ModelTier, String>> asMap() {
    Map<String, Map<ModelTier, String>> map = new HashMap<>();
    map.put("openai", tiers(Map.of(
        ModelTier.LITE, "gpt-5.4-nano",
        ModelTier.STANDARD, "gpt-5.4-mini",
        ModelTier.POWERFUL, "gpt-5.5",
        ModelTier.PREMIUM, "gpt-5.5")));
    map.put("claude", tiers(Map.of(
        ModelTier.LITE, "claude-haiku-4-5-20251001",
        ModelTier.STANDARD, "claude-sonnet-4-6",
        ModelTier.POWERFUL, "claude-opus-4-8",
        ModelTier.PREMIUM, "claude-opus-4-8")));
    map.put("gemini", tiers(Map.of(
        ModelTier.LITE, "gemini-3.1-flash-lite",
        ModelTier.STANDARD, "gemini-3.5-flash",
        ModelTier.POWERFUL, "gemini-3.1-pro",
        ModelTier.PREMIUM, "gemini-3.1-pro")));
    map.put("mistral", tiers(Map.of(
        ModelTier.LITE, "mistral-small-2603",
        ModelTier.STANDARD, "mistral-medium-3-5",
        ModelTier.POWERFUL, "mistral-large-2512",
        ModelTier.PREMIUM, "mistral-large-2512")));
    map.put("bedrock", tiers(Map.of(
        ModelTier.LITE, "anthropic.claude-haiku-4-5-20251001-v1:0",
        ModelTier.STANDARD, "anthropic.claude-sonnet-4-6",
        ModelTier.POWERFUL, "anthropic.claude-opus-4-8",
        ModelTier.PREMIUM, "anthropic.claude-opus-4-8")));
    map.put("azure-openai", tiers(Map.of(
        ModelTier.LITE, "gpt-5.4-nano",
        ModelTier.STANDARD, "gpt-5.4-mini",
        ModelTier.POWERFUL, "gpt-5.5",
        ModelTier.PREMIUM, "gpt-5.5")));
    map.put("ollama", tiers(Map.of(
        ModelTier.LITE, "qwen3:4b",
        ModelTier.STANDARD, "qwen3:14b",
        ModelTier.POWERFUL, "qwen3:32b",
        ModelTier.PREMIUM, "qwen3:32b")));
    map.put("vllm", tiers(Map.of(
        ModelTier.LITE, "Qwen/Qwen3-4B",
        ModelTier.STANDARD, "Qwen/Qwen3-14B",
        ModelTier.POWERFUL, "Qwen/Qwen3-32B",
        ModelTier.PREMIUM, "Qwen/Qwen3-32B")));
    map.put("openai-compatible", tiers(Map.of(
        ModelTier.LITE, "gpt-5.4-nano",
        ModelTier.STANDARD, "gpt-5.4-mini",
        ModelTier.POWERFUL, "gpt-5.5",
        ModelTier.PREMIUM, "gpt-5.5")));
    return map;
  }

  /**
   * Copies one provider's declared tier entries into a fresh {@link EnumMap}. Every entry names its
   * {@link ModelTier} beside its model string, so a mapping cannot be shifted onto the wrong tier by
   * argument position, and a repeated tier key is rejected by {@link Map#of} at construction.
   *
   * @param byTier the provider's tier→model entries
   *
   * @return a mutable {@code EnumMap} copy; never {@code null}
   */
  private static Map<ModelTier, String> tiers(Map<ModelTier, String> byTier) {
    return new EnumMap<>(byTier);
  }
}
