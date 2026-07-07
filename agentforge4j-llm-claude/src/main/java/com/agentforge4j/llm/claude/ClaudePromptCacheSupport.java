// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.DefaultTokenEstimator;
import com.agentforge4j.llm.PromptLayerCacheSupport;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.claude.dto.ClaudeSystemContentBlock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Splits an assembled system prompt into Anthropic {@code system} content blocks and applies
 * {@code cache_control} markers from {@link PromptLayerBoundaries}.
 * <p>
 * Layer slicing and breakpoint selection are shared with other providers via
 * {@link PromptLayerCacheSupport}; this class supplies the Claude-specific model-minimum-tokens
 * table and DTO construction.
 * <p>
 * Token estimates come from the shared {@link DefaultTokenEstimator} heuristic
 * ({@code ceil(utf8ByteLength / 4)}), a conservative fallback when the provider does not expose a
 * tokenizer.
 */
final class ClaudePromptCacheSupport {

  private static final System.Logger LOG = System.getLogger(
      ClaudePromptCacheSupport.class.getName());

  /**
   * Default minimum estimated tokens in a layer segment for a cache breakpoint when the model id is
   * not listed in {@link #MODEL_MIN_CACHEABLE_SEGMENT_TOKENS}.
   */
  static final int DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS =
      PromptLayerCacheSupport.DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS;

  /**
   * Anthropic per-model minimum cacheable segment lengths (estimated tokens). Keys are matched with
   * {@link String#startsWith(String)} against the request model id (date suffixes allowed).
   * Unrecognized models use {@link #DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS}.
   */
  private static final Map<String, Integer> MODEL_MIN_CACHEABLE_SEGMENT_TOKENS = Map.of(
      "claude-haiku-4-5", 4096,
      "claude-3-5-haiku", 2048);

  private ClaudePromptCacheSupport() {
  }

  /**
   * Resolves the minimum cacheable segment length for a Claude model id.
   *
   * @param modelId request model identifier (non-blank)
   * @return minimum estimated tokens required before a layer may receive {@code cache_control}
   */
  static int resolveMinCacheableSegmentTokens(String modelId) {
    return PromptLayerCacheSupport.resolveMinCacheableSegmentTokens(
        modelId, MODEL_MIN_CACHEABLE_SEGMENT_TOKENS);
  }

  /**
   * Builds the {@code system} content blocks for a Claude Messages API request.
   *
   * @param systemPrompt          assembled system prompt text
   * @param promptLayerBoundaries layer end offsets, or {@code null} when caching is disabled
   * @param modelId               resolved Claude model id (used when boundaries are present)
   * @return one or more system content blocks
   */
  static List<ClaudeSystemContentBlock> buildSystemBlocks(
      String systemPrompt,
      PromptLayerBoundaries promptLayerBoundaries,
      String modelId) {
    return PromptLayerCacheSupport.buildSystemBlocks(
        systemPrompt,
        promptLayerBoundaries,
        modelId,
        MODEL_MIN_CACHEABLE_SEGMENT_TOKENS,
        (text, cacheBreakpoint) -> cacheBreakpoint
            ? ClaudeSystemContentBlock.cachedText(text)
            : ClaudeSystemContentBlock.plainText(text));
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
   * @param modelId               resolved Claude model id
   * @return per-layer marker flags
   */
  static boolean[] selectBreakpoints(PromptLayerBoundaries promptLayerBoundaries,
      String modelId) {
    boolean[] mark = PromptLayerCacheSupport.selectBreakpoints(
        promptLayerBoundaries, modelId, MODEL_MIN_CACHEABLE_SEGMENT_TOKENS);
    LOG.log(
        System.Logger.Level.DEBUG,
        "prompt-cache modelId=%s thresholds=%s mark=%s".formatted(modelId, promptLayerBoundaries,
            Arrays.toString(mark)));
    return mark;
  }

  /**
   * Estimates token count from a UTF-8 byte length using the shared {@link DefaultTokenEstimator}.
   *
   * @param utf8ByteLength segment size in UTF-8 bytes
   * @return estimated token count (at least 1 when length is positive)
   */
  static int estimateTokens(int utf8ByteLength) {
    return DefaultTokenEstimator.estimateFromUtf8ByteLength(utf8ByteLength);
  }
}
