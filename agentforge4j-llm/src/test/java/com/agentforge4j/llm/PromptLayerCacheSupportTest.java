// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.PromptLayerBoundaries;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct tests for the shared prompt-cache layer handling. The Claude and Bedrock suites remain the
 * provider contract tests — they pin each provider's own thresholds and DTO shape; this pins the
 * behaviour both of them now route through.
 */
class PromptLayerCacheSupportTest {

  /** Threshold table with two overlapping prefixes, so longest-prefix selection is observable. */
  private static final Map<String, Integer> OVERLAPPING = Map.of(
      "model", 512,
      "model-large", 4096);

  private static final Map<String, Integer> NO_MATCH = Map.of("other", 99);

  /** Builds a plain "text|marked" rendering so block order and marker placement are both visible. */
  private static List<String> blocks(String prompt, PromptLayerBoundaries boundaries,
      String modelId, Map<String, Integer> table) {
    return PromptLayerCacheSupport.buildSystemBlocks(prompt, boundaries, modelId, table,
        (text, cacheBreakpoint) -> text + "|" + cacheBreakpoint);
  }

  // ---------------------------------------------------------------- threshold resolution

  @Test
  void unmatchedModelFallsBackToTheDefaultThreshold() {
    assertThat(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens("unknown-model", NO_MATCH))
        .isEqualTo(PromptLayerCacheSupport.DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS)
        .isEqualTo(1024);
  }

  @Test
  void matchedPrefixWinsOverTheDefault() {
    assertThat(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens("model-x", OVERLAPPING))
        .isEqualTo(512);
  }

  @Test
  void longestMatchingPrefixWins() {
    // "model-large-1" starts with both "model" and "model-large"; the more specific entry must win.
    assertThat(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens("model-large-1",
        OVERLAPPING)).isEqualTo(4096);
  }

  @Test
  void longestPrefixSelectionDoesNotDependOnMapIterationOrder() {
    // Same two entries, both insertion orders. A first-match implementation returns a different
    // answer for one of these; a longest-match implementation cannot.
    Map<String, Integer> shortFirst = new LinkedHashMap<>();
    shortFirst.put("model", 512);
    shortFirst.put("model-large", 4096);

    Map<String, Integer> longFirst = new LinkedHashMap<>();
    longFirst.put("model-large", 4096);
    longFirst.put("model", 512);

    assertThat(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens("model-large-1", shortFirst))
        .isEqualTo(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens("model-large-1",
            longFirst))
        .isEqualTo(4096);
  }

  @Test
  void blankModelIdAndNullTableAreRejected() {
    assertThatThrownBy(
        () -> PromptLayerCacheSupport.resolveMinCacheableSegmentTokens("  ", OVERLAPPING))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
        () -> PromptLayerCacheSupport.resolveMinCacheableSegmentTokens("model", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---------------------------------------------------------------- breakpoint selection

  @Test
  void breakpointsUseTheCumulativePrefixNotTheIndividualSlice() {
    // Layer 2 is a single byte on its own — far below 1024 tokens — but the prefix through it is
    // 8000 bytes (2000 tokens). Anthropic caches from the start of the prompt through the marked
    // block, so the prefix is what must clear the threshold. Per-slice logic would leave this off.
    PromptLayerBoundaries boundaries = new PromptLayerBoundaries(7999, 8000, null);

    boolean[] mark = PromptLayerCacheSupport.selectBreakpoints(boundaries, "unknown", NO_MATCH);

    assertThat(mark[0]).isTrue();
    assertThat(mark[1]).isTrue();
  }

  @Test
  void layerExactlyOnTheThresholdIsMarked() {
    // 4096 bytes is exactly 1024 tokens, and the comparison is >=, so this must be inclusive.
    boolean[] mark = PromptLayerCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(4096, 4096, null), "unknown", NO_MATCH);

    assertThat(mark[0]).isTrue();
  }

  @Test
  void oneByteEitherSideOfTheThresholdFlipsTheDecision() {
    // 4093 bytes -> ceil(4093/4) == 1024 tokens, still on the threshold.
    assertThat(PromptLayerCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(4093, 4093, null), "unknown", NO_MATCH)[0]).isTrue();
    // 4092 bytes -> exactly 1023 tokens, one below.
    assertThat(PromptLayerCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(4092, 4092, null), "unknown", NO_MATCH)[0]).isFalse();
  }

  @Test
  void providerThresholdIsAppliedInsteadOfTheDefault() {
    // 8192 bytes == 2048 tokens: over the 1024 default, under the 4096 the table asks for.
    PromptLayerBoundaries boundaries = new PromptLayerBoundaries(8192, 8192, null);

    assertThat(PromptLayerCacheSupport.selectBreakpoints(boundaries, "unknown", NO_MATCH)[0])
        .isTrue();
    assertThat(PromptLayerCacheSupport.selectBreakpoints(boundaries, "model-large-1",
        OVERLAPPING)[0]).isFalse();
  }

  @Test
  void absentLayerThreeIsNeverMarked() {
    boolean[] mark = PromptLayerCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(8192, 8192, null), "unknown", NO_MATCH);

    assertThat(mark).hasSize(3);
    assertThat(mark[2]).isFalse();
  }

  @Test
  void presentLayerThreeIsJudgedOnItsOwnCumulativePrefix() {
    boolean[] mark = PromptLayerCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(100, 200, 8192), "unknown", NO_MATCH);

    assertThat(mark[0]).isFalse();
    assertThat(mark[1]).isFalse();
    assertThat(mark[2]).isTrue();
  }

  // ---------------------------------------------------------------- slicing

  @Test
  void slicesSplitThePromptAtExactUtf8Offsets() {
    byte[] utf8 = "abcdefghij".getBytes(StandardCharsets.UTF_8);

    assertThat(PromptLayerCacheSupport.sliceLayers(utf8, new PromptLayerBoundaries(3, 7, 10)))
        .containsExactly("abc", "defg", "hij");
  }

  @Test
  void slicesCutMultibyteContentOnCharacterBoundaries() {
    // Three euro signs, three UTF-8 bytes each. Cutting at byte 3 and 6 lands between characters.
    byte[] utf8 = "€€€".getBytes(StandardCharsets.UTF_8);
    assertThat(utf8).hasSize(9);

    assertThat(PromptLayerCacheSupport.sliceLayers(utf8, new PromptLayerBoundaries(3, 6, 9)))
        .containsExactly("€", "€", "€");
  }

  @Test
  void emptyLayersProduceEmptySlicesRatherThanBeingDropped() {
    byte[] utf8 = "abcd".getBytes(StandardCharsets.UTF_8);

    // Empty layer 1 (0..0), then empty layer 2 (0..0), then the content in layer 3.
    assertThat(PromptLayerCacheSupport.sliceLayers(utf8, new PromptLayerBoundaries(0, 0, 4)))
        .containsExactly("", "", "abcd");
    // Empty layer 2 between two populated layers.
    assertThat(PromptLayerCacheSupport.sliceLayers(utf8, new PromptLayerBoundaries(2, 2, 4)))
        .containsExactly("ab", "", "cd");
    // Empty layer 3 at the end.
    assertThat(PromptLayerCacheSupport.sliceLayers(utf8, new PromptLayerBoundaries(2, 4, 4)))
        .containsExactly("ab", "cd", "");
  }

  @Test
  void descendingOffsetsAreRejectedBeforeSlicingIsEverReached() {
    // The gate for out-of-order layers is PromptLayerBoundaries itself, so slicing can never see a
    // descending pair. Asserted here so the ordering contract stays pinned from this side too —
    // the matching guard inside sliceLayers is defence in depth, not the enforcement point.
    assertThatThrownBy(() -> new PromptLayerBoundaries(7, 3, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("layer1EndOffset must be <= layer2EndOffset");
    assertThatThrownBy(() -> new PromptLayerBoundaries(3, 7, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("layer2EndOffset must be <= layer3EndOffset");
  }

  @Test
  void offsetsBeyondThePromptAreRejected() {
    byte[] utf8 = "abcd".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
        () -> PromptLayerCacheSupport.sliceLayers(utf8, new PromptLayerBoundaries(2, 99, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not exceed");
  }

  // ---------------------------------------------------------------- block construction

  @Test
  void nullBoundariesProduceOneUnmarkedBlockHoldingTheWholePrompt() {
    assertThat(blocks("the whole prompt", null, "irrelevant", NO_MATCH))
        .containsExactly("the whole prompt|false");
  }

  @Test
  void nullBoundariesDoNotRequireAModelId() {
    // Caching is off, so there is no threshold to resolve and no model id to demand.
    assertThat(blocks("prompt", null, null, NO_MATCH)).containsExactly("prompt|false");
  }

  @Test
  void blocksFollowLayerOrderAndCarryTheirOwnMarker() {
    // Layer 1 is 4096 bytes (1024 tokens, exactly on the default threshold) so it is marked;
    // layer 2 extends it, so it is marked too. Content order must survive unchanged.
    String prompt = "a".repeat(4096) + "b".repeat(8);

    List<String> result = blocks(prompt, new PromptLayerBoundaries(4096, 4104, null), "unknown",
        NO_MATCH);

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).isEqualTo("a".repeat(4096) + "|true");
    assertThat(result.get(1)).isEqualTo("b".repeat(8) + "|true");
  }

  @Test
  void unmarkedLayersStayUnmarked() {
    List<String> result = blocks("abcdefghij", new PromptLayerBoundaries(3, 7, 10), "unknown",
        NO_MATCH);

    assertThat(result).containsExactly("abc|false", "defg|false", "hij|false");
  }

  @Test
  void concatenatedBlocksReproduceTheAssembledPrompt() {
    // Offsets derived from the real UTF-8 lengths rather than hand-counted, so the multibyte
    // characters cannot silently shift a boundary into the middle of one.
    String layer1 = "system rules€";
    String layer2 = " agent prompt€";
    String layer3 = " step prompt";
    String prompt = layer1 + layer2 + layer3;
    int end1 = layer1.getBytes(StandardCharsets.UTF_8).length;
    int end2 = end1 + layer2.getBytes(StandardCharsets.UTF_8).length;
    int end3 = prompt.getBytes(StandardCharsets.UTF_8).length;

    List<String> result = blocks(prompt, new PromptLayerBoundaries(end1, end2, end3), "unknown",
        NO_MATCH);

    assertThat(result).containsExactly(layer1 + "|false", layer2 + "|false", layer3 + "|false");
    assertThat(result.stream().map(block -> block.substring(0, block.lastIndexOf('|')))
        .reduce("", String::concat)).isEqualTo(prompt);
  }

  @Test
  void nullPromptAndNullFactoryAreRejected() {
    assertThatThrownBy(() -> blocks(null, null, "m", NO_MATCH))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PromptLayerCacheSupport.buildSystemBlocks("p", null, "m", NO_MATCH,
        null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankModelIdIsRejectedOnlyWhenCachingIsEnabled() {
    assertThatThrownBy(
        () -> blocks("prompt", new PromptLayerBoundaries(3, 6, null), "  ", NO_MATCH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("prompt caching is enabled");
  }
}
