// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.PromptLayerCacheSupport;
import com.agentforge4j.llm.Utf8TokenEstimate;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.bedrock.dto.BedrockSystemContentBlock;
import java.util.List;
import java.util.Map;

/**
 * Splits an assembled system prompt into Bedrock Anthropic {@code system} content blocks and applies
 * {@code cache_control} markers from {@link PromptLayerBoundaries}.
 * <p>
 * Slicing, threshold resolution and breakpoint selection are shared with the other Anthropic-style
 * provider via {@link PromptLayerCacheSupport}. What stays here is what is genuinely
 * Bedrock Anthropic-specific: the per-model minimum-cacheable-segment table below, and turning a layer
 * slice into a {@link BedrockSystemContentBlock}.
 */
final class BedrockPromptCacheSupport {

  /**
   * Default minimum estimated tokens in a layer segment for a cache breakpoint when the model id is
   * not listed in {@link #MODEL_MIN_CACHEABLE_SEGMENT_TOKENS}.
   */
  static final int DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS =
      PromptLayerCacheSupport.DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS;

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
   * @param modelId               resolved Bedrock Anthropic model id (used when boundaries are present)
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
   * Threshold checks use the cumulative UTF-8 prefix length at each layer boundary (Anthropic
   * caches from the start of the prompt through the marked block), not the individual layer slice.
   *
   * @param promptLayerBoundaries layer end offsets
   * @param modelId               resolved Bedrock Anthropic model id
   * @return per-layer marker flags
   */
  static boolean[] selectBreakpoints(PromptLayerBoundaries promptLayerBoundaries,
      String modelId) {
    // No logging here: the shared implementation already logs the decision, and repeating it would
    // emit the line twice for every cached request.
    return PromptLayerCacheSupport.selectBreakpoints(
        promptLayerBoundaries, modelId, MODEL_MIN_CACHEABLE_SEGMENT_TOKENS);
  }

  /**
   * Estimates token count from a UTF-8 byte length.
   *
   * @param utf8ByteLength segment size in UTF-8 bytes
   * @return estimated token count (at least 1 when length is positive)
   */
  static int estimateTokens(int utf8ByteLength) {
    return Utf8TokenEstimate.fromUtf8ByteLength(utf8ByteLength);
  }
}
