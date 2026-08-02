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
 * <p>Boundaries are byte offsets into the prompt's UTF-8 encoding, and every offset is checked
 * against that encoding before it is used: an offset landing inside a multi-byte character is
 * rejected rather than decoded into replacement characters, so a caller working in {@link String}
 * index space finds out instead of shipping a corrupted prompt to the model.
 *
 * <p>Token estimates come from the shared {@link Utf8TokenEstimate} heuristic, a conservative
 * fallback for providers that expose no tokenizer.
 */
public final class PromptLayerCacheSupport {

  private static final System.Logger LOG =
      System.getLogger(PromptLayerCacheSupport.class.getName());

  /** Mask selecting the two high bits that identify a UTF-8 continuation byte. */
  private static final int UTF8_CONTINUATION_MASK = 0xC0;

  /** Value of those two high bits on a UTF-8 continuation byte ({@code 10xxxxxx}). */
  private static final int UTF8_CONTINUATION_BITS = 0x80;

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
     * @return provider-specific system content block; must not be {@code null}
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
   *                               {@link String#startsWith(String)}) to minimum estimated tokens;
   *                               neither keys nor values may be {@code null}
   *
   * @return minimum estimated tokens required before a layer may receive {@code cache_control};
   *         {@link #DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS} when nothing matches
   *
   * @throws IllegalArgumentException if {@code modelId} is blank, the table is {@code null}, or the
   *                                  table holds a {@code null} key or value
   */
  public static int resolveMinCacheableSegmentTokens(
      String modelId, Map<String, Integer> modelPrefixToMinTokens) {
    Validate.notBlank(modelId, "modelId must not be blank");
    Validate.notNull(modelPrefixToMinTokens, "modelPrefixToMinTokens must not be null");
    Map.Entry<String, Integer> longestMatch = null;
    for (Map.Entry<String, Integer> entry : modelPrefixToMinTokens.entrySet()) {
      String prefix = entry.getKey();
      Validate.notNull(prefix, "modelPrefixToMinTokens must not contain a null model prefix");
      Validate.notNull(entry.getValue(),
          () -> new IllegalArgumentException(
              "modelPrefixToMinTokens must not contain a null minimum token count; prefix %s"
                  .formatted(prefix)));
      if (modelId.startsWith(prefix)
          && (longestMatch == null || prefix.length() > longestMatch.getKey().length())) {
        longestMatch = entry;
      }
    }
    return longestMatch != null ? longestMatch.getValue() : DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS;
  }

  /**
   * Builds the {@code system} content blocks for an assembled system prompt, applying
   * {@code cache_control} markers per {@link #selectBreakpoints}.
   *
   * <p>When {@code promptLayerBoundaries} is present, layers 1 and 2 are required and layer 3 is
   * optional, so the result is two blocks — or three when layer 3 is present. A boundaries value
   * whose layer 1 or layer 2 offset is absent describes a shape this slicing does not represent and
   * is rejected rather than silently reinterpreted.
   *
   * @param systemPrompt           assembled system prompt text; must not be {@code null}
   * @param promptLayerBoundaries  layer end offsets, or {@code null} when caching is disabled
   * @param modelId                resolved provider model id; required when boundaries are present
   * @param modelPrefixToMinTokens provider model-prefix to minimum-cacheable-segment-tokens table
   * @param blockFactory           creates the provider-specific DTO for one layer slice; must not
   *                               be {@code null} and must not return {@code null}
   * @param <T>                    provider-specific system content block DTO type
   *
   * @return one block holding the whole prompt when caching is disabled, otherwise two blocks — or
   *         three when layer 3 is present — in layer order
   *
   * @throws IllegalArgumentException if any argument is invalid, a layer end offset is absent, out
   *                                  of range or lands inside a multi-byte UTF-8 character, or the
   *                                  factory returns {@code null}
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
      T wholePrompt = blockFactory.create(systemPrompt, false);
      Validate.notNull(wholePrompt, "blockFactory must not return null");
      return List.of(wholePrompt);
    }
    Validate.notBlank(modelId, "modelId must not be blank when prompt caching is enabled");
    byte[] utf8 = systemPrompt.getBytes(StandardCharsets.UTF_8);
    List<String> slices = sliceLayers(utf8, promptLayerBoundaries);
    boolean[] markBreakpoint =
        selectBreakpoints(promptLayerBoundaries, modelId, modelPrefixToMinTokens);
    List<T> blocks = new ArrayList<>(slices.size());
    for (int index = 0; index < slices.size(); index++) {
      T block = blockFactory.create(slices.get(index), markBreakpoint[index]);
      int layerNumber = index + 1;
      Validate.notNull(block,
          () -> new IllegalArgumentException(
              "blockFactory must not return null; layer %d".formatted(layerNumber)));
      blocks.add(block);
    }
    return List.copyOf(blocks);
  }

  /**
   * Validates that a boundaries value describes a shape this class can slice: layers 1 and 2
   * present, layer 3 optional.
   *
   * <p>{@link PromptLayerBoundaries} accepts a {@code null} layer 1 or layer 2 to mean "that layer
   * is not present" — a legal value of that record, but not a prompt shape these providers emit;
   * the framework's own assembly always produces both. Rejecting it here by name keeps the failure
   * an explicit {@code IllegalArgumentException} instead of an unboxing
   * {@code NullPointerException} several frames deeper.
   *
   * @param boundaries layer end offsets
   */
  private static void requireSliceableBoundaries(PromptLayerBoundaries boundaries) {
    Validate.notNull(boundaries, "promptLayerBoundaries must not be null");
    Validate.notNull(boundaries.layer1EndOffset(),
        "layer1EndOffset must be present when prompt caching is enabled");
    Validate.notNull(boundaries.layer2EndOffset(),
        "layer2EndOffset must be present when prompt caching is enabled");
  }

  /**
   * Splits an assembled prompt's UTF-8 bytes into per-layer slices at the given boundaries.
   *
   * <p>Slicing happens in byte space rather than on the {@link String}, and every offset is checked
   * against the encoding first: an offset landing inside a multi-byte character is rejected with an
   * {@code IllegalArgumentException}. Decoding it instead would substitute {@code U+FFFD}
   * replacement characters, silently corrupting the prompt and breaking the guarantee that the
   * slices concatenate back to the original.
   *
   * @param utf8       assembled system prompt encoded as UTF-8 bytes
   * @param boundaries layer end offsets; layers 1 and 2 must be present
   *
   * @return the text of each layer, in layer order — two entries, or three when layer 3 is present
   */
  static List<String> sliceLayers(byte[] utf8, PromptLayerBoundaries boundaries) {
    requireSliceableBoundaries(boundaries);
    List<String> slices = new ArrayList<>(3);
    appendSlice(slices, utf8, 0, boundaries.layer1EndOffset());
    appendSlice(slices, utf8, boundaries.layer1EndOffset(), boundaries.layer2EndOffset());
    if (boundaries.layer3EndOffset() != null) {
      appendSlice(slices, utf8, boundaries.layer2EndOffset(), boundaries.layer3EndOffset());
    }
    return List.copyOf(slices);
  }

  private static void appendSlice(
      List<String> slices,
      byte[] utf8,
      int startOffset,
      Integer endOffset) {
    Validate.notNull(endOffset, "layer end offset must not be null when slicing");
    Validate.isTrue(endOffset >= startOffset,
        "layer end offset must not precede start offset");
    Validate.isTrue(endOffset <= utf8.length,
        "layer end offset must not exceed assembled prompt UTF-8 length");
    requireCodePointBoundary(utf8, endOffset);
    if (endOffset == startOffset) {
      slices.add("");
      return;
    }
    slices.add(new String(utf8, startOffset, endOffset - startOffset, StandardCharsets.UTF_8));
  }

  /**
   * Rejects an offset pointing at a UTF-8 continuation byte, i.e. one that would split a multi-byte
   * character. Offset {@code 0} and the end-of-array offset are always valid split points.
   *
   * @param utf8   assembled system prompt encoded as UTF-8 bytes
   * @param offset candidate split point
   */
  private static void requireCodePointBoundary(byte[] utf8, int offset) {
    if (offset <= 0 || offset >= utf8.length) {
      return;
    }
    Validate.isTrue((utf8[offset] & UTF8_CONTINUATION_MASK) != UTF8_CONTINUATION_BITS,
        () -> new IllegalArgumentException(
            ("layer end offset %d falls inside a multi-byte UTF-8 character; layer offsets are "
                + "UTF-8 byte offsets, not String indices").formatted(offset)));
  }

  /**
   * Selects which layer blocks receive {@code cache_control}.
   *
   * <p>Each layer is judged on the <em>cumulative</em> UTF-8 prefix from the start of the prompt
   * through that layer's end offset, not on the layer's own slice: Anthropic caches from the start
   * of the prompt through the marked block, so the prefix is what has to clear the threshold. A
   * short layer therefore still qualifies once everything before it is long enough.
   *
   * @param promptLayerBoundaries  layer end offsets; layers 1 and 2 must be present
   * @param modelId                resolved provider model id
   * @param modelPrefixToMinTokens provider model-prefix to minimum-cacheable-segment-tokens table
   *
   * @return per-layer marker flags, always length 3; index 2 stays {@code false} when layer 3 is
   *         absent
   *
   * @throws IllegalArgumentException if the boundaries are {@code null}, layer 1 or layer 2 is
   *                                  absent, or the model id or threshold table is invalid
   */
  public static boolean[] selectBreakpoints(
      PromptLayerBoundaries promptLayerBoundaries,
      String modelId,
      Map<String, Integer> modelPrefixToMinTokens) {
    requireSliceableBoundaries(promptLayerBoundaries);
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
