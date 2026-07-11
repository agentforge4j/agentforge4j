// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.PromptLayerBoundaries;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLayerCacheSupportTest {

  private static final Map<String, Integer> MODEL_MIN_TOKENS = Map.of(
      "model-with-high-threshold", 4096,
      "model-with-low-threshold", 2048);

  private static String utf8Chars(int byteCount) {
    return "x".repeat(byteCount);
  }

  private static int utf8Length(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  private static PromptLayerBoundaries boundariesFor(
      String layer1, String separator, String layer2, String layer3) {
    int layer1End = utf8Length(layer1);
    int layer2End = utf8Length(layer1 + separator + layer2);
    Integer layer3End =
        layer3 == null ? null : utf8Length(layer1 + separator + layer2 + separator + layer3);
    return new PromptLayerBoundaries(layer1End, layer2End, layer3End);
  }

  @Test
  void resolveMinCacheableSegmentTokens_matchesLongestConfiguredPrefix() {
    assertThat(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens(
        "model-with-high-threshold-v2", MODEL_MIN_TOKENS)).isEqualTo(4096);
    assertThat(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens(
        "model-with-low-threshold-v2", MODEL_MIN_TOKENS)).isEqualTo(2048);
  }

  @Test
  void resolveMinCacheableSegmentTokens_overlappingPrefixesResolveToTheMostSpecific() {
    // "model" and "model-pro" both match "model-pro-v2"; the longer (more specific) prefix must win
    // deterministically, regardless of the map's unordered iteration.
    Map<String, Integer> overlapping = Map.of(
        "model", 1111,
        "model-pro", 2222);

    assertThat(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens(
        "model-pro-v2", overlapping)).isEqualTo(2222);
    assertThat(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens(
        "model-lite-v2", overlapping)).isEqualTo(1111);
  }

  @Test
  void resolveMinCacheableSegmentTokens_unrecognizedModelUsesDefault() {
    assertThat(PromptLayerCacheSupport.resolveMinCacheableSegmentTokens(
        "unrecognized-model", MODEL_MIN_TOKENS))
        .isEqualTo(PromptLayerCacheSupport.DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS);
  }

  @Test
  void resolveMinCacheableSegmentTokens_rejectsBlankModelId() {
    assertThatThrownBy(() -> PromptLayerCacheSupport.resolveMinCacheableSegmentTokens(
        " ", MODEL_MIN_TOKENS))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void selectBreakpoints_marksDeeperLayerWhenCumulativePrefixClearsThreshold() {
    // "unrecognized-model" falls back to the 1024-token default: estimateTokens(2400)=600 (<1024),
    // estimateTokens(4800)=1200 (>=1024).
    boolean[] marked = PromptLayerCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(2400, 4800, null), "unrecognized-model", MODEL_MIN_TOKENS);

    assertThat(marked).containsExactly(false, true, false);
  }

  @Test
  void selectBreakpoints_evaluatesEachLayerIndependentlyWithNoCapOnCount() {
    boolean[] marked = PromptLayerCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(8192, 8192, 8192), "unrecognized-model", MODEL_MIN_TOKENS);

    // All three layers clear the 1024-token default threshold; there is no cap on the number of
    // breakpoints marked, since Anthropic itself does not impose a fixed cap independent of the
    // requested boundaries.
    assertThat(marked).containsExactly(true, true, true);
  }

  @Test
  void selectBreakpoints_skipsLayer3WhenAbsent() {
    boolean[] marked = PromptLayerCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(8192, 8192, null), "unrecognized-model", MODEL_MIN_TOKENS);

    assertThat(marked).containsExactly(true, true, false);
  }

  @Test
  void sliceLayers_splitsAtBoundariesAndConcatenationMatchesAssembledPrompt() {
    String layer1 = utf8Chars(100);
    String separator = "\n\n";
    String layer2 = utf8Chars(200);
    String layer3 = utf8Chars(300);
    String assembled = layer1 + separator + layer2 + separator + layer3;
    PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, layer3);

    List<PromptLayerCacheSupport.LayerSlice> slices = PromptLayerCacheSupport.sliceLayers(
        assembled.getBytes(StandardCharsets.UTF_8), boundaries);

    assertThat(slices).hasSize(3);
    String joined = slices.stream()
        .map(PromptLayerCacheSupport.LayerSlice::text)
        .reduce("", String::concat);
    assertThat(joined).isEqualTo(assembled);
  }

  @Test
  void sliceLayers_absentLayer3ProducesTwoSlices() {
    String layer1 = utf8Chars(50);
    String separator = "\n\n";
    String layer2 = utf8Chars(60);
    String assembled = layer1 + separator + layer2;
    PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, null);

    List<PromptLayerCacheSupport.LayerSlice> slices = PromptLayerCacheSupport.sliceLayers(
        assembled.getBytes(StandardCharsets.UTF_8), boundaries);

    assertThat(slices).hasSize(2);
    assertThat(slices.get(0).text()).isEqualTo(layer1);
    assertThat(slices.get(1).text()).isEqualTo(separator + layer2);
  }

  @Test
  void buildSystemBlocks_nullBoundariesProducesSinglePlainBlock() {
    List<String> blocks = PromptLayerCacheSupport.buildSystemBlocks(
        "plain prompt", null, "any-model", MODEL_MIN_TOKENS,
        (text, cacheBreakpoint) -> cacheBreakpoint ? "CACHED:" + text : "PLAIN:" + text);

    assertThat(blocks).containsExactly("PLAIN:plain prompt");
  }

  @Test
  void buildSystemBlocks_marksLayer2WhenCumulativePrefixClearsThreshold() {
    String layer1 = utf8Chars(2400);
    String separator = "\n\n";
    String layer2 = utf8Chars(2400);
    String assembled = layer1 + separator + layer2;
    PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, null);

    List<String> blocks = PromptLayerCacheSupport.buildSystemBlocks(
        assembled, boundaries, "unrecognized-model", MODEL_MIN_TOKENS,
        (text, cacheBreakpoint) -> cacheBreakpoint ? "CACHED" : "PLAIN");

    assertThat(blocks).containsExactly("PLAIN", "CACHED");
  }

  @Test
  void buildSystemBlocks_rejectsBlankModelIdWhenBoundariesPresent() {
    PromptLayerBoundaries boundaries = new PromptLayerBoundaries(4, 8, null);

    assertThatThrownBy(() -> PromptLayerCacheSupport.buildSystemBlocks(
        "abcdefgh", boundaries, " ", MODEL_MIN_TOKENS,
        (text, cacheBreakpoint) -> text))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
