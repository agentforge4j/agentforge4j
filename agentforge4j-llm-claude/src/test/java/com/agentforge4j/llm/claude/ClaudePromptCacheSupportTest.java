package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.claude.dto.ClaudeSystemContentBlock;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudePromptCacheSupportTest {

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
  void selectBreakpoints_marksDeeperLayerWhenCumulativePrefixClearsThreshold() {
    boolean[] marked = ClaudePromptCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(2400, 4800, null), "claude-sonnet-4-20250514");

    assertThat(ClaudePromptCacheSupport.estimateTokens(2400)).isLessThan(1024);
    assertThat(ClaudePromptCacheSupport.estimateTokens(4800)).isGreaterThanOrEqualTo(1024);
    assertThat(marked).containsExactly(false, true, false);
  }

  @Test
  void selectBreakpoints_skipsShallowLayerWhenCumulativePrefixBelowThreshold() {
    boolean[] marked = ClaudePromptCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(100, 4096, 4096), "claude-sonnet-4-20250514");

    assertThat(marked).containsExactly(false, true, true);
  }

  @Test
  void buildSystemBlocks_marksLayer2WhenCumulativePrefixClearsThreshold() {
    String layer1 = utf8Chars(2400);
    String separator = "\n\n";
    String layer2 = utf8Chars(2400);
    String assembled = layer1 + separator + layer2;
    PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, null);

    List<ClaudeSystemContentBlock> blocks = ClaudePromptCacheSupport.buildSystemBlocks(
        assembled, boundaries, "claude-sonnet-4-20250514");

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0).cacheControl()).isNull();
    assertThat(blocks.get(1).cacheControl()).isNotNull();
  }

  @Test
  void selectBreakpoints_prefersDeepestLayersWhenCapExceeded() {
    boolean[] marked = ClaudePromptCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(1023, 4096, 4096), "claude-3-opus-20240229");

    assertThat(marked).containsExactly(false, true, true);
  }

  @Test
  void selectBreakpoints_haiku45_skipsLayerBetween1024And4096EstimatedTokens() {
    boolean[] marked = ClaudePromptCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(8192, 8192, null), "claude-haiku-4-5-20251001");

    assertThat(marked).containsExactly(false, false, false);
    assertThat(ClaudePromptCacheSupport.estimateTokens(8192)).isEqualTo(2048);
  }

  @Test
  void selectBreakpoints_sonnetMarksLayerThatClears1024Default() {
    boolean[] marked = ClaudePromptCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(8192, 8192, null), "claude-sonnet-4-20250514");

    assertThat(marked).containsExactly(true, true, false);
  }

  @Test
  void selectBreakpoints_unrecognizedModelUses1024Default() {
    assertThat(ClaudePromptCacheSupport.resolveMinCacheableSegmentTokens("claude-unknown-99"))
        .isEqualTo(ClaudePromptCacheSupport.DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS);

    boolean[] marked = ClaudePromptCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(8192, 8192, null), "claude-unknown-99");

    assertThat(marked).containsExactly(true, true, false);
  }

  @Test
  void buildSystemBlocks_concatenationMatchesAssembledPrompt() {
    String layer1 = utf8Chars(100);
    String separator = "\n\n";
    String layer2 = utf8Chars(200);
    String layer3 = utf8Chars(300);
    String assembled = layer1 + separator + layer2 + separator + layer3;
    PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, layer3);

    List<ClaudeSystemContentBlock> blocks =
        ClaudePromptCacheSupport.buildSystemBlocks(assembled, boundaries, "claude-3-opus-20240229");

    String joined = blocks.stream().map(ClaudeSystemContentBlock::text).reduce("", String::concat);
    assertThat(joined).isEqualTo(assembled);
  }

  @Test
  void buildSystemBlocks_absentLayer3_producesTwoBlocks() {
    String layer1 = utf8Chars(50);
    String separator = "\n\n";
    String layer2 = utf8Chars(60);
    String assembled = layer1 + separator + layer2;
    PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, null);

    List<ClaudeSystemContentBlock> blocks =
        ClaudePromptCacheSupport.buildSystemBlocks(assembled, boundaries, "claude-3-opus-20240229");

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0).text()).isEqualTo(layer1);
    assertThat(blocks.get(1).text()).isEqualTo(separator + layer2);
  }
}
