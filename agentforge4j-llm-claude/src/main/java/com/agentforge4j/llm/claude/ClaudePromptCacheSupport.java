// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.PromptLayerCacheSupport;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.claude.dto.ClaudeSystemContentBlock;
import java.util.List;
import java.util.Map;

/**
 * Splits an assembled system prompt into Claude {@code system} content blocks and applies
 * {@code cache_control} markers from {@link PromptLayerBoundaries}.
 * <p>
 * Slicing, threshold resolution and breakpoint selection are shared with the other Anthropic-style
 * provider via {@link PromptLayerCacheSupport}. What stays here is what is genuinely
 * Claude-specific: the per-model minimum-cacheable-segment table below, and turning a layer
 * slice into a {@link ClaudeSystemContentBlock}.
 */
final class ClaudePromptCacheSupport {

  /**
   * Claude per-model minimum cacheable segment lengths (estimated tokens). Keys are matched with
   * {@link String#startsWith(String)} against the request model id (date suffixes allowed).
   * Unrecognized models fall back to
   * {@link PromptLayerCacheSupport#DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS}.
   */
  private static final Map<String, Integer> MODEL_MIN_CACHEABLE_SEGMENT_TOKENS = Map.of(
      "claude-haiku-4-5", 4096,
      "claude-3-5-haiku", 2048);

  private ClaudePromptCacheSupport() {
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
}
