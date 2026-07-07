// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.util.Validate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared prompt-layer slicing and cache-breakpoint selection logic for providers that expose an
 * Anthropic-style {@code system} content-block array with {@code cache_control} markers (Claude
 * Messages API and Bedrock Anthropic InvokeModel).
 * <p>
 * Splitting an assembled system prompt into layer slices and deciding which layer boundaries
 * qualify for a cache breakpoint is identical across those providers; only the resulting DTO type
 * and the per-model minimum-cacheable-segment-length table differ. Callers supply both as
 * parameters so this class stays provider-agnostic.
 * <p>
 * Token estimates come from the shared {@link DefaultTokenEstimator} heuristic
 * ({@code ceil(utf8ByteLength / 4)}), a conservative fallback when the provider does not expose a
 * tokenizer.
 */
public final class PromptLayerCacheSupport {

  /**
   * Default minimum estimated tokens in a layer segment for a cache breakpoint when the model id is
   * not listed in a provider's model-prefix-to-minimum-tokens map.
   */
  public static final int DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS = 1024;

  private PromptLayerCacheSupport() {
  }

  /**
   * Produces a provider-specific system content block from a layer slice's text and whether that
   * layer received a cache breakpoint.
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
     * @return provider-specific system content block
     */
    T create(String text, boolean cacheBreakpoint);
  }

  /**
   * A contiguous slice of the assembled system prompt corresponding to one prompt layer.
   *
   * @param text            slice text
   * @param utf8ByteLength  slice length in UTF-8 bytes
   */
  public record LayerSlice(String text, int utf8ByteLength) {

  }

  /**
   * Resolves the minimum cacheable segment length for a model id against a provider-supplied
   * model-prefix-to-minimum-tokens table.
   *
   * @param modelId                   request model identifier (non-blank)
   * @param modelPrefixToMinTokens    provider model-prefix (matched with
   *                                  {@link String#startsWith(String)}) to minimum estimated tokens
   * @return minimum estimated tokens required before a layer may receive {@code cache_control}
   */
  public static int resolveMinCacheableSegmentTokens(
      String modelId, Map<String, Integer> modelPrefixToMinTokens) {
    Validate.notBlank(modelId, "modelId must not be blank");
    Validate.notNull(modelPrefixToMinTokens, "modelPrefixToMinTokens must not be null");
    for (Map.Entry<String, Integer> entry : modelPrefixToMinTokens.entrySet()) {
      if (modelId.startsWith(entry.getKey())) {
        return entry.getValue();
      }
    }
    return DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS;
  }

  /**
   * Builds the {@code system} content blocks for an assembled system prompt, applying
   * {@code cache_control} markers per {@link #selectBreakpoints}.
   *
   * @param systemPrompt           assembled system prompt text
   * @param promptLayerBoundaries  layer end offsets, or {@code null} when caching is disabled
   * @param modelId                resolved provider model id (used when boundaries are present)
   * @param modelPrefixToMinTokens provider model-prefix to minimum-cacheable-segment-tokens table
   * @param blockFactory           creates the provider-specific DTO for one layer slice
   * @param <T>                    provider-specific system content block DTO type
   * @return one or more system content blocks
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
    List<LayerSlice> slices = sliceLayers(utf8, promptLayerBoundaries);
    boolean[] markBreakpoint =
        selectBreakpoints(promptLayerBoundaries, modelId, modelPrefixToMinTokens);
    List<T> blocks = new ArrayList<>(slices.size());
    for (int index = 0; index < slices.size(); index++) {
      blocks.add(blockFactory.create(slices.get(index).text(), markBreakpoint[index]));
    }
    return List.copyOf(blocks);
  }

  /**
   * Splits an assembled prompt's UTF-8 bytes into per-layer slices at the given boundaries.
   *
   * @param utf8      assembled system prompt encoded as UTF-8 bytes
   * @param boundaries layer end offsets
   * @return one slice per present layer (two or three, depending on whether layer 3 is present)
   */
  public static List<LayerSlice> sliceLayers(byte[] utf8, PromptLayerBoundaries boundaries) {
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
   * Selects which layer blocks receive {@code cache_control}.
   * <p>
   * Each layer is checked independently: a layer receives a breakpoint when the estimated token
   * count of the cumulative UTF-8 prefix through that layer's end offset meets or exceeds the
   * resolved threshold. Threshold checks use the cumulative prefix length at each layer boundary
   * (Anthropic caches from the start of the prompt through the marked block), not the individual
   * layer slice length.
   *
   * @param promptLayerBoundaries  layer end offsets
   * @param modelId                resolved provider model id
   * @param modelPrefixToMinTokens provider model-prefix to minimum-cacheable-segment-tokens table
   * @return per-layer marker flags
   */
  public static boolean[] selectBreakpoints(
      PromptLayerBoundaries promptLayerBoundaries,
      String modelId,
      Map<String, Integer> modelPrefixToMinTokens) {
    int threshold = resolveMinCacheableSegmentTokens(modelId, modelPrefixToMinTokens);
    boolean[] mark = new boolean[3];
    mark[0] = DefaultTokenEstimator.estimateFromUtf8ByteLength(
        promptLayerBoundaries.layer1EndOffset()) >= threshold;
    mark[1] = DefaultTokenEstimator.estimateFromUtf8ByteLength(
        promptLayerBoundaries.layer2EndOffset()) >= threshold;
    if (promptLayerBoundaries.layer3EndOffset() != null) {
      mark[2] = DefaultTokenEstimator.estimateFromUtf8ByteLength(
          promptLayerBoundaries.layer3EndOffset()) >= threshold;
    }
    return mark;
  }
}
