// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.ModelTier;

import java.util.EnumMap;
import java.util.Map;

/**
 * The shipped OSS default mapping of {@link ModelTier} to a concrete, versioned model string for
 * each of the nine built-in providers (design §6). These are the out-of-the-box defaults; operators
 * may override any provider/tier entry, and an embedding application may further override them.
 *
 * <p>Local providers ({@code ollama}, {@code vllm}) default to Qwen models; {@code azure-openai}
 * uses OpenAI model names (the Azure deployment name is configured separately); {@code bedrock}
 * uses Anthropic Claude model identifiers.
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
    Map<String, Map<ModelTier, String>> map = new java.util.HashMap<>();
    map.put("openai", tiers("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.5"));
    map.put("claude",
        tiers("claude-haiku-4-5-20251001", "claude-sonnet-4-6", "claude-opus-4-8"));
    map.put("gemini", tiers("gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3.1-pro"));
    map.put("mistral", tiers("mistral-small-2603", "mistral-medium-3-5", "mistral-large-2512"));
    map.put("bedrock", tiers(
        "anthropic.claude-haiku-4-5-20251001-v1:0",
        "anthropic.claude-sonnet-4-6",
        "anthropic.claude-opus-4-8"));
    map.put("azure-openai", tiers("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.5"));
    map.put("ollama", tiers("qwen3:4b", "qwen3:14b", "qwen3:32b"));
    map.put("vllm", tiers("Qwen/Qwen3-4B", "Qwen/Qwen3-14B", "Qwen/Qwen3-32B"));
    map.put("openai-compatible", tiers("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.5"));
    return map;
  }

  private static Map<ModelTier, String> tiers(String lite, String standard, String powerful) {
    Map<ModelTier, String> byTier = new EnumMap<>(ModelTier.class);
    byTier.put(ModelTier.LITE, lite);
    byTier.put(ModelTier.STANDARD, standard);
    byTier.put(ModelTier.POWERFUL, powerful);
    return byTier;
  }
}
