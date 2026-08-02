// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.claude.dto.ClaudeSystemContentBlock;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Claude's provider contract: its own per-model threshold table, and the DTO a layer slice becomes.
 * The shared slicing and breakpoint algorithm is pinned once in {@code PromptLayerCacheSupportTest}.
 *
 * <p>Every assertion here runs through {@code buildSystemBlocks} — the only entry point production
 * uses — so a change that stopped passing this provider's table to the shared implementation would
 * fail these tests rather than pass a parallel path that nothing calls.
 */
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

  /** Which layers came back carrying {@code cache_control}, in layer order. */
  private static List<Boolean> marks(PromptLayerBoundaries boundaries, String modelId) {
    int promptBytes = boundaries.layer3EndOffset() != null
        ? boundaries.layer3EndOffset()
        : boundaries.layer2EndOffset();
    return ClaudePromptCacheSupport
        .buildSystemBlocks(utf8Chars(promptBytes), boundaries, modelId)
        .stream()
        .map(block -> block.cacheControl() != null)
        .toList();
  }

  @Test
  void marksDeeperLayerWhenCumulativePrefixClearsThreshold() {
    // 2400 bytes -> 600 estimated tokens (under 1024); 4800 -> 1200 (over).
    assertThat(marks(new PromptLayerBoundaries(2400, 4800, null), "claude-sonnet-4-20250514"))
        .containsExactly(false, true);
  }

  @Test
  void skipsShallowLayerWhenCumulativePrefixBelowThreshold() {
    assertThat(marks(new PromptLayerBoundaries(100, 4096, 4096), "claude-sonnet-4-20250514"))
        .containsExactly(false, true, true);
  }

  @Test
  void haiku45AppliesItsOwn4096Threshold() {
    // 8192 bytes == 2048 estimated tokens: over the 1024 default, under the 4096 this model wants.
    assertThat(marks(new PromptLayerBoundaries(8192, 8192, null), "claude-haiku-4-5-20251001"))
        .containsExactly(false, false);
    // 16384 bytes == 4096 tokens, exactly on this model's threshold.
    assertThat(marks(new PromptLayerBoundaries(16384, 16384, null), "claude-haiku-4-5-20251001"))
        .containsExactly(true, true);
  }

  @Test
  void haiku35AppliesItsOwn2048Threshold() {
    // Pins the "claude-3-5-haiku" -> 2048 row specifically, from both sides:
    // 4096 bytes == 1024 tokens clears the 1024 default but not this model's 2048 ...
    assertThat(marks(new PromptLayerBoundaries(4096, 4096, null), "claude-3-5-haiku-20241022"))
        .containsExactly(false, false);
    // ... and 8192 bytes == 2048 tokens clears 2048 but would not clear haiku-4-5's 4096.
    assertThat(marks(new PromptLayerBoundaries(8192, 8192, null), "claude-3-5-haiku-20241022"))
        .containsExactly(true, true);
  }

  @Test
  void sonnetMarksLayerThatClearsThe1024Default() {
    assertThat(marks(new PromptLayerBoundaries(8192, 8192, null), "claude-sonnet-4-20250514"))
        .containsExactly(true, true);
  }

  @Test
  void unrecognizedModelUsesThe1024Default() {
    // 4096 bytes == 1024 tokens, exactly on the default ...
    assertThat(marks(new PromptLayerBoundaries(4096, 4096, null), "claude-unknown-99"))
        .containsExactly(true, true);
    // ... and 4092 bytes == 1023 tokens, one below it.
    assertThat(marks(new PromptLayerBoundaries(4092, 4092, null), "claude-unknown-99"))
        .containsExactly(false, false);
  }

  @Test
  void buildSystemBlocksMarksLayer2WhenCumulativePrefixClearsThreshold() {
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
  void buildSystemBlocksConcatenationMatchesAssembledPrompt() {
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
  void buildSystemBlocksAbsentLayer3ProducesTwoBlocks() {
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

  @Test
  void multibyteLayerBoundariesRoundTripThroughTheProviderPath() {
    // A Claude request whose layers end on multi-byte characters must still reassemble exactly.
    String layer1 = "systeem regels€";
    String separator = "\n\n";
    String layer2 = "agent prompt€";
    String assembled = layer1 + separator + layer2;
    PromptLayerBoundaries boundaries = boundariesFor(layer1, separator, layer2, null);

    List<ClaudeSystemContentBlock> blocks =
        ClaudePromptCacheSupport.buildSystemBlocks(assembled, boundaries, "claude-3-opus-20240229");

    assertThat(blocks.stream().map(ClaudeSystemContentBlock::text).reduce("", String::concat))
        .isEqualTo(assembled);
    assertThat(blocks.get(0).text()).isEqualTo(layer1);
  }
}
