// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.DefaultTokenEstimator;
import com.agentforge4j.llm.PromptLayerCacheSupport;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.bedrock.dto.BedrockSystemContentBlock;
import java.util.List;
import java.util.Map;

/**
 * Splits an assembled system prompt into Bedrock Anthropic InvokeModel {@code system} content
 * blocks and applies {@code cache_control} markers from {@link PromptLayerBoundaries}.
 * <p>
 * Layer slicing and breakpoint selection are shared with other providers via
 * {@link PromptLayerCacheSupport}; this class supplies the Bedrock-specific model-minimum-tokens
 * table and DTO construction.
 * <p>
 * Token estimates come from the shared {@link DefaultTokenEstimator} heuristic
 * ({@code ceil(utf8ByteLength / 4)}), a conservative fallback when the provider does not expose a
 * tokenizer.
 */
final class BedrockPromptCacheSupport {

  /**
   * Default minimum estimated tokens in a layer segment for a cache breakpoint when the model id is
   * not listed in {@link #MODEL_MIN_CACHEABLE_SEGMENT_TOKENS}.
   */
  static final int DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS =
      PromptLayerCacheSupport.DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS;

  /**
   * Bedrock Anthropic per-model minimum cacheable segment lengths (estimated tokens). Keys are
   * matched with {@link String#startsWith(String)} against the request model id (version suffixes
   * allowed). Unrecognized models use {@link #DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS}.
   */
  private static final Map<String, Integer> MODEL_MIN_CACHEABLE_SEGMENT_TOKENS = Map.of(
      "anthropic.claude-haiku-4-5", 4096,
      "anthropic.claude-3-5-haiku", 2048);

  private BedrockPromptCacheSupport() {
  }

  /**
   * Resolves the minimum cacheable segment length for a Bedrock Anthropic model id.
   *
   * @param modelId request model identifier (non-blank)
   * @return minimum estimated tokens required before a layer may receive {@code cache_control}
   */
  static int resolveMinCacheableSegmentTokens(String modelId) {
    return PromptLayerCacheSupport.resolveMinCacheableSegmentTokens(
        modelId, MODEL_MIN_CACHEABLE_SEGMENT_TOKENS);
  }

  /**
   * Builds the {@code system} content blocks for a Bedrock Anthropic InvokeModel request.
   *
   * @param systemPrompt          assembled system prompt text
   * @param promptLayerBoundaries layer end offsets, or {@code null} when caching is disabled
   * @param modelId               resolved Bedrock Anthropic model id (used when boundaries are
   *                              present)
   * @return one or more system content blocks
   */
  static List<BedrockSystemContentBlock> buildSystemBlocks(
      String systemPrompt,
      PromptLayerBoundaries promptLayerBoundaries,
      String modelId) {
    return PromptLayerCacheSupport.buildSystemBlocks(
        systemPrompt,
        promptLayerBoundaries,
        modelId,
        MODEL_MIN_CACHEABLE_SEGMENT_TOKENS,
        (text, cacheBreakpoint) -> cacheBreakpoint
            ? BedrockSystemContentBlock.cachedText(text)
            : BedrockSystemContentBlock.plainText(text));
  }

  /**
   * Selects which layer blocks receive {@code cache_control}.
   * <p>
   * Each layer is checked independently: a layer receives a breakpoint when the estimated token
   * count of the cumulative UTF-8 prefix through that layer's end offset meets or exceeds the
   * resolved threshold. Threshold checks use the cumulative UTF-8 prefix length at each layer
   * boundary (Anthropic caches from the start of the prompt through the marked block), not the
   * individual layer slice.
   *
   * @param promptLayerBoundaries layer end offsets
   * @param modelId               resolved Bedrock Anthropic model id
   * @return per-layer marker flags
   */
  static boolean[] selectBreakpoints(PromptLayerBoundaries promptLayerBoundaries, String modelId) {
    // The shared selectBreakpoints logs the per-request breakpoint decision; logging it here too
    // would duplicate the line for callers of this wrapper.
    return PromptLayerCacheSupport.selectBreakpoints(
        promptLayerBoundaries, modelId, MODEL_MIN_CACHEABLE_SEGMENT_TOKENS);
  }

  /**
   * Estimates token count from a UTF-8 byte length using the shared {@link DefaultTokenEstimator}.
   *
   * @param utf8ByteLength segment size in UTF-8 bytes
   * @return estimated token count (zero when length is not positive)
   */
  static int estimateTokens(int utf8ByteLength) {
    return DefaultTokenEstimator.estimateFromUtf8ByteLength(utf8ByteLength);
  }
}
