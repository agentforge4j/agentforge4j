// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.util.Validate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Prompt-layer slicing and cache-breakpoint selection for providers exposing an Anthropic-style
 * {@code system} content-block array with {@code cache_control} markers — currently the Claude
 * Messages API and Bedrock Anthropic InvokeModel adapters.
 *
 * <p>Everything about that decision is provider-independent: where the layer boundaries fall, how
 * the prompt is sliced at them, and which boundaries clear the minimum cacheable length. Only two
 * things differ between providers, and both arrive as parameters — the per-model threshold table
 * and the DTO the resulting blocks are built into.
 *
 * <p>Token estimates come from the shared {@link Utf8TokenEstimate} heuristic, a conservative
 * fallback for providers that expose no tokenizer.
 */
public final class PromptLayerCacheSupport {

  private static final System.Logger LOG =
      System.getLogger(PromptLayerCacheSupport.class.getName());

  /**
   * Minimum estimated tokens a cumulative prefix must reach before its layer may carry a cache
   * breakpoint, when the model id matches no entry in the provider's table.
   */
  public static final int DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS = 1024;

  private PromptLayerCacheSupport() {
  }

  /**
   * Builds the provider-specific system content block for one layer slice.
   *
   * @param <T> provider-specific system content block DTO type
   */
  @FunctionalInterface
  public interface SystemBlockFactory<T> {

    /**
     * Creates a system content block for one layer slice.
     *
     * @param text            layer slice text
     * @param cacheBreakpoint {@code true} when this layer must carry a {@code cache_control} marker
     *
     * @return provider-specific system content block
     */
    T create(String text, boolean cacheBreakpoint);
  }

  /**
   * Resolves the minimum cacheable segment length for a model id against a provider's
   * model-prefix table.
   *
   * <p>When several configured prefixes match, the longest — most specific — one wins. The
   * providers' shipped tables hold mutually exclusive prefixes, so this changes no shipped
   * behaviour; it makes the outcome a property of the table rather than of {@link Map} iteration
   * order, which is unspecified and so would otherwise decide the threshold for any future table
   * where one prefix extends another.
   *
   * @param modelId                request model identifier (non-blank)
   * @param modelPrefixToMinTokens provider model-prefix (matched with
   *                               {@link String#startsWith(String)}) to minimum estimated tokens
   *
   * @return minimum estimated tokens required before a layer may receive {@code cache_control};
   *         {@link #DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS} when nothing matches
   */
  public static int resolveMinCacheableSegmentTokens(
      String modelId, Map<String, Integer> modelPrefixToMinTokens) {
    Validate.notBlank(modelId, "modelId must not be blank");
    Validate.notNull(modelPrefixToMinTokens, "modelPrefixToMinTokens must not be null");
    Map.Entry<String, Integer> longestMatch = null;
    for (Map.Entry<String, Integer> entry : modelPrefixToMinTokens.entrySet()) {
      if (modelId.startsWith(entry.getKey())
          && (longestMatch == null || entry.getKey().length() > longestMatch.getKey().length())) {
        longestMatch = entry;
      }
    }
    return longestMatch != null ? longestMatch.getValue() : DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS;
  }

  /**
   * Builds the {@code system} content blocks for an assembled system prompt, applying
   * {@code cache_control} markers per {@link #selectBreakpoints}.
   *
   * @param systemPrompt           assembled system prompt text; must not be {@code null}
   * @param promptLayerBoundaries  layer end offsets, or {@code null} when caching is disabled
   * @param modelId                resolved provider model id; required when boundaries are present
   * @param modelPrefixToMinTokens provider model-prefix to minimum-cacheable-segment-tokens table
   * @param blockFactory           creates the provider-specific DTO for one layer slice
   * @param <T>                    provider-specific system content block DTO type
   *
   * @return one block holding the whole prompt when caching is disabled, otherwise one block per
   *         present layer, in layer order
   */
  public static <T> List<T> buildSystemBlocks(
      String systemPrompt,
      PromptLayerBoundaries promptLayerBoundaries,
      String modelId,
      Map<String, Integer> modelPrefixToMinTokens,
      SystemBlockFactory<T> blockFactory) {
    Validate.notNull(systemPrompt, "systemPrompt must not be null");
    Validate.notNull(blockFactory, "blockFactory must not be null");
    if (promptLayerBoundaries == null) {
      return List.of(blockFactory.create(systemPrompt, false));
    }
    Validate.notBlank(modelId, "modelId must not be blank when prompt caching is enabled");
    byte[] utf8 = systemPrompt.getBytes(StandardCharsets.UTF_8);
    List<String> slices = sliceLayers(utf8, promptLayerBoundaries);
    boolean[] markBreakpoint =
        selectBreakpoints(promptLayerBoundaries, modelId, modelPrefixToMinTokens);
    List<T> blocks = new ArrayList<>(slices.size());
    for (int index = 0; index < slices.size(); index++) {
      blocks.add(blockFactory.create(slices.get(index), markBreakpoint[index]));
    }
    return List.copyOf(blocks);
  }

  /**
   * Splits an assembled prompt's UTF-8 bytes into per-layer slices at the given boundaries. Slicing
   * happens in byte space rather than on the {@link String}, so a boundary falling inside a
   * multi-byte character is a caller error rather than a silently mangled slice.
   *
   * @param utf8       assembled system prompt encoded as UTF-8 bytes
   * @param boundaries layer end offsets
   *
   * @return the text of each present layer, in layer order — two entries, or three when layer 3
   *         is present
   */
  static List<String> sliceLayers(byte[] utf8, PromptLayerBoundaries boundaries) {
    List<String> slices = new ArrayList<>(3);
    appendSliceIfPresent(slices, utf8, 0, boundaries.layer1EndOffset());
    appendSliceIfPresent(slices, utf8, boundaries.layer1EndOffset(), boundaries.layer2EndOffset());
    if (boundaries.layer3EndOffset() != null) {
      appendSliceIfPresent(slices, utf8, boundaries.layer2EndOffset(),
          boundaries.layer3EndOffset());
    }
    return List.copyOf(slices);
  }

  private static void appendSliceIfPresent(
      List<String> slices,
      byte[] utf8,
      int startOffset,
      Integer endOffset) {
    Validate.notNull(endOffset, "layer end offset must not be null when slicing");
    Validate.isTrue(endOffset >= startOffset,
        "layer end offset must not precede start offset");
    Validate.isTrue(endOffset <= utf8.length,
        "layer end offset must not exceed assembled prompt UTF-8 length");
    if (endOffset == startOffset) {
      slices.add("");
      return;
    }
    slices.add(new String(utf8, startOffset, endOffset - startOffset, StandardCharsets.UTF_8));
  }

  /**
   * Selects which layer blocks receive {@code cache_control}.
   *
   * <p>Each layer is judged on the <em>cumulative</em> UTF-8 prefix from the start of the prompt
   * through that layer's end offset, not on the layer's own slice: Anthropic caches from the start
   * of the prompt through the marked block, so the prefix is what has to clear the threshold. A
   * short layer therefore still qualifies once everything before it is long enough.
   *
   * @param promptLayerBoundaries  layer end offsets
   * @param modelId                resolved provider model id
   * @param modelPrefixToMinTokens provider model-prefix to minimum-cacheable-segment-tokens table
   *
   * @return per-layer marker flags, always length 3; index 2 stays {@code false} when layer 3 is
   *         absent
   */
  public static boolean[] selectBreakpoints(
      PromptLayerBoundaries promptLayerBoundaries,
      String modelId,
      Map<String, Integer> modelPrefixToMinTokens) {
    int threshold = resolveMinCacheableSegmentTokens(modelId, modelPrefixToMinTokens);
    boolean[] mark = new boolean[3];
    mark[0] = Utf8TokenEstimate.fromUtf8ByteLength(
        promptLayerBoundaries.layer1EndOffset()) >= threshold;
    mark[1] = Utf8TokenEstimate.fromUtf8ByteLength(
        promptLayerBoundaries.layer2EndOffset()) >= threshold;
    if (promptLayerBoundaries.layer3EndOffset() != null) {
      mark[2] = Utf8TokenEstimate.fromUtf8ByteLength(
          promptLayerBoundaries.layer3EndOffset()) >= threshold;
    }
    // Logged here and only here — every production buildSystemBlocks call routes through this
    // method, so the per-request cache decision stays observable without each provider wrapper
    // repeating the line. Supplier form because this runs on every cached LLM call and the message
    // must not be built when DEBUG is off.
    LOG.log(
        System.Logger.Level.DEBUG,
        () -> "prompt-cache modelId=%s thresholds=%s mark=%s".formatted(modelId,
            promptLayerBoundaries, Arrays.toString(mark)));
    return mark;
  }
}
