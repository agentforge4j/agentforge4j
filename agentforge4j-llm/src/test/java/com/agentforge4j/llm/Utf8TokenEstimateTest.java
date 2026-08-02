// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the default token heuristic that the Claude and Bedrock prompt-cache paths share. The
 * numbers matter to behaviour: each one sits at a threshold boundary where being off by one token
 * flips a cache breakpoint on or off.
 */
class Utf8TokenEstimateTest {

  @Test
  void nonPositiveLengthsEstimateToZero() {
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(0)).isZero();
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(-1)).isZero();
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(Integer.MIN_VALUE)).isZero();
  }

  @Test
  void positiveLengthsRoundUpToTheNextWholeToken() {
    // The ceiling is what makes a 1-byte segment cost a token rather than nothing.
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(1)).isEqualTo(1);
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(2)).isEqualTo(1);
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(3)).isEqualTo(1);
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(4)).isEqualTo(1);
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(5)).isEqualTo(2);
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(8)).isEqualTo(2);
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(9)).isEqualTo(3);
  }

  @Test
  void exactMultiplesOfFourDoNotGainAnExtraToken() {
    // Guards the off-by-one that ceil() invites: 4096/4 is 1024 exactly, and 1024 is the default
    // cacheable-segment threshold, so an extra token here would silently start marking segments
    // that sit exactly on the boundary.
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(4096)).isEqualTo(1024);
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(8192)).isEqualTo(2048);
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(16384)).isEqualTo(4096);
  }

  @Test
  void multibyteTextCostsMoreThanItsCharacterCountSuggests() {
    // The heuristic is byte-based, not char-based. "€" is 1 char but 3 UTF-8 bytes, so 8 of them
    // are 24 bytes -> 6 tokens, where a char-count heuristic would have said 2. Callers pass byte
    // lengths precisely so this distinction is theirs to get right, and this pins the arithmetic
    // they depend on.
    String euros = "€".repeat(8);
    assertThat(euros).hasSize(8);
    int utf8Length = euros.getBytes(StandardCharsets.UTF_8).length;
    assertThat(utf8Length).isEqualTo(24);

    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(utf8Length)).isEqualTo(6);
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(euros.length())).isEqualTo(2);
  }

  @Test
  void largeLengthsDoNotOverflowIntoNegativeEstimates() {
    assertThat(Utf8TokenEstimate.fromUtf8ByteLength(Integer.MAX_VALUE)).isPositive();
  }
}
