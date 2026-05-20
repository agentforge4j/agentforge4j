package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.claude.dto.ClaudeSystemContentBlock;
import com.agentforge4j.util.Validate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Splits an assembled system prompt into Anthropic {@code system} content blocks and applies
 * {@code cache_control} markers from {@link PromptLayerBoundaries}.
 * <p>
 * Token estimates use {@value #BYTES_PER_TOKEN_ESTIMATE} UTF-8 bytes per token (ceiling), a
 * conservative heuristic when the provider does not expose a tokenizer.
 */
final class ClaudePromptCacheSupport {

  private static final System.Logger LOG = System.getLogger(
      ClaudePromptCacheSupport.class.getName());

  /**
   * Default minimum estimated tokens in a layer segment for a cache breakpoint when the model id is
   * not listed in {@link #MODEL_MIN_CACHEABLE_SEGMENT_TOKENS}.
   */
  static final int DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS = 1024;

  /**
   * Anthropic per-model minimum cacheable segment lengths (estimated tokens). Keys are matched with
   * {@link String#startsWith(String)} against the request model id (date suffixes allowed).
   * Unrecognized models use {@link #DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS}.
   */
  private static final Map<String, Integer> MODEL_MIN_CACHEABLE_SEGMENT_TOKENS = Map.of(
      "claude-haiku-4-5", 4096,
      "claude-3-5-haiku", 2048);

  /**
   * Bytes-per-token estimate for threshold checks ({@code ceil(utf8Length / 4)}).
   */
  static final double BYTES_PER_TOKEN_ESTIMATE = 4.0;

  private ClaudePromptCacheSupport() {
  }

  /**
   * Resolves the minimum cacheable segment length for a Claude model id.
   *
   * @param modelId request model identifier (non-blank)
   * @return minimum estimated tokens required before a layer may receive {@code cache_control}
   */
  static int resolveMinCacheableSegmentTokens(String modelId) {
    Validate.notBlank(modelId, "modelId must not be blank");
    for (Map.Entry<String, Integer> entry : MODEL_MIN_CACHEABLE_SEGMENT_TOKENS.entrySet()) {
      if (modelId.startsWith(entry.getKey())) {
        return entry.getValue();
      }
    }
    return DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS;
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
    Validate.notNull(systemPrompt, "systemPrompt must not be null");
    if (promptLayerBoundaries == null) {
      return List.of(ClaudeSystemContentBlock.plainText(systemPrompt));
    }
    Validate.notBlank(modelId, "modelId must not be blank when prompt caching is enabled");
    byte[] utf8 = systemPrompt.getBytes(StandardCharsets.UTF_8);
    List<LayerSlice> slices = sliceLayers(utf8, promptLayerBoundaries);
    boolean[] markBreakpoint = selectBreakpoints(promptLayerBoundaries,
        modelId);
    List<ClaudeSystemContentBlock> blocks = new ArrayList<>(slices.size());
    for (int index = 0; index < slices.size(); index++) {
      String text = slices.get(index).text();
      if (markBreakpoint[index]) {
        blocks.add(ClaudeSystemContentBlock.cachedText(text));
      } else {
        blocks.add(ClaudeSystemContentBlock.plainText(text));
      }
    }
    return List.copyOf(blocks);
  }

  private static List<LayerSlice> sliceLayers(byte[] utf8, PromptLayerBoundaries boundaries) {
    List<LayerSlice> slices = new ArrayList<>(3);
    appendSliceIfPresent(slices, utf8, 0, boundaries.layer1EndOffset());
    appendSliceIfPresent(slices, utf8, boundaries.layer1EndOffset(), boundaries.layer2EndOffset());
    if (boundaries.layer3EndOffset() != null) {
      appendSliceIfPresent(slices, utf8, boundaries.layer2EndOffset(),
          boundaries.layer3EndOffset());
    }
    return List.copyOf(slices);
  }

  private static void appendSliceIfPresent(
      List<LayerSlice> slices,
      byte[] utf8,
      int startOffset,
      Integer endOffset) {
    Validate.notNull(endOffset, "layer end offset must not be null when slicing");
    Validate.isTrue(endOffset >= startOffset,
        "layer end offset must not precede start offset");
    Validate.isTrue(endOffset <= utf8.length,
        "layer end offset must not exceed assembled prompt UTF-8 length");
    if (endOffset == startOffset) {
      slices.add(new LayerSlice("", 0));
      return;
    }
    int segmentUtf8Length = endOffset - startOffset;
    String text = new String(utf8, startOffset, segmentUtf8Length, StandardCharsets.UTF_8);
    slices.add(new LayerSlice(text, segmentUtf8Length));
  }

  /**
   * Selects which layer blocks receive {@code cache_control}, deepest-first within
   * {@code maxBreakpoints}.
   * <p>
   * Threshold checks use the cumulative UTF-8 prefix length at each layer boundary (Anthropic
   * caches from the start of the prompt through the marked block), not the individual layer slice.
   *
   * @param promptLayerBoundaries layer end offsets
   * @param modelId               resolved Claude model id
   * @return per-layer marker flags
   */
  static boolean[] selectBreakpoints(PromptLayerBoundaries promptLayerBoundaries,
      String modelId) {
    int threshold = resolveMinCacheableSegmentTokens(modelId);
    boolean[] mark = new boolean[3];
    mark[0] = estimateTokens(promptLayerBoundaries.layer1EndOffset()) >= threshold;
    mark[1] = estimateTokens(promptLayerBoundaries.layer2EndOffset()) >= threshold;
    if (promptLayerBoundaries.layer3EndOffset() != null) {
      mark[2] = estimateTokens(promptLayerBoundaries.layer3EndOffset()) >= threshold;
    }
    LOG.log(
        System.Logger.Level.DEBUG,
        "prompt-cache modelId=%s thresholds=%s mark=%s".formatted(modelId, promptLayerBoundaries,
            Arrays.toString(mark)));
    return mark;
  }

  /**
   * Estimates token count from a UTF-8 byte length using {@link #BYTES_PER_TOKEN_ESTIMATE}.
   *
   * @param utf8ByteLength segment size in UTF-8 bytes
   * @return estimated token count (at least 1 when length is positive)
   */
  static int estimateTokens(int utf8ByteLength) {
    if (utf8ByteLength <= 0) {
      return 0;
    }
    return (int) Math.ceil((double) utf8ByteLength / BYTES_PER_TOKEN_ESTIMATE);
  }

  record LayerSlice(String text, int utf8ByteLength) {

  }
}
