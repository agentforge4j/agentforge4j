package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.bedrock.dto.BedrockSystemContentBlock;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BedrockPromptCacheSupportTest {

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
    boolean[] marked = BedrockPromptCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(2400, 4800, null),
        "anthropic.claude-sonnet-4-20250514-v1:0");

    assertThat(BedrockPromptCacheSupport.estimateTokens(2400)).isLessThan(1024);
    assertThat(BedrockPromptCacheSupport.estimateTokens(4800)).isGreaterThanOrEqualTo(1024);
    assertThat(marked[0]).isFalse();
    assertThat(marked[1]).isTrue();
  }

  @Test
  void buildSystemBlocks_marksLayer2WhenCumulativePrefixClearsThreshold() {
    String layer1 = utf8Chars(2400);
    String separator = "\n\n";
    String layer2 = utf8Chars(2400);
    String assembled = layer1 + separator + layer2;
    PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, null);

    List<BedrockSystemContentBlock> blocks = BedrockPromptCacheSupport.buildSystemBlocks(
        assembled, boundaries, "anthropic.claude-sonnet-4-20250514-v1:0");

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0).cacheControl()).isNull();
    assertThat(blocks.get(1).cacheControl()).isNotNull();
  }

  @Test
  void selectBreakpoints_haiku45_skipsLayerBetween1024And4096EstimatedTokens() {
    boolean[] marked = BedrockPromptCacheSupport.selectBreakpoints(
        new PromptLayerBoundaries(8192, 8192, null),
        "anthropic.claude-haiku-4-5-20251001-v1:0");

    assertThat(marked[0]).isFalse();
    assertThat(BedrockPromptCacheSupport.estimateTokens(8192)).isEqualTo(2048);
  }

  @Test
  void buildSystemBlocks_concatenationMatchesAssembledPrompt() {
    String layer1 = utf8Chars(100);
    String separator = "\n\n";
    String layer2 = utf8Chars(200);
    String layer3 = utf8Chars(300);
    String assembled = layer1 + separator + layer2 + separator + layer3;
    PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, layer3);

    List<BedrockSystemContentBlock> blocks = BedrockPromptCacheSupport.buildSystemBlocks(
        assembled, boundaries, "anthropic.claude-3-opus-20240229-v1:0");

    String joined = blocks.stream().map(BedrockSystemContentBlock::text).reduce("", String::concat);
    assertThat(joined).isEqualTo(assembled);
  }
}
