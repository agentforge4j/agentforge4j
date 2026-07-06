// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultTokenEstimatorTest {

  private final DefaultTokenEstimator estimator = new DefaultTokenEstimator();

  @Test
  void emptyTextEstimatesToZero() {
    assertThat(estimator.estimate("")).isZero();
  }

  @Test
  void asciiTextRoundsUpAtFourBytesPerToken() {
    assertThat(estimator.estimate("x")).isEqualTo(1);
    assertThat(estimator.estimate("xxxx")).isEqualTo(1);
    assertThat(estimator.estimate("xxxxx")).isEqualTo(2);
    assertThat(estimator.estimate("x".repeat(8))).isEqualTo(2);
  }

  @Test
  void multibyteTextEstimatesFromUtf8ByteLengthNotCharCount() {
    // "€" is three UTF-8 bytes: ceil(3/4) == 1; two euro signs are six bytes: ceil(6/4) == 2.
    assertThat(estimator.estimate("€")).isEqualTo(1);
    assertThat(estimator.estimate("€€")).isEqualTo(2);
  }

  @Test
  void nullTextIsRejected() {
    assertThatThrownBy(() -> estimator.estimate(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void byteLengthEntryPointMatchesCeilingHeuristic() {
    assertThat(DefaultTokenEstimator.estimateFromUtf8ByteLength(0)).isZero();
    assertThat(DefaultTokenEstimator.estimateFromUtf8ByteLength(1)).isEqualTo(1);
    assertThat(DefaultTokenEstimator.estimateFromUtf8ByteLength(4)).isEqualTo(1);
    assertThat(DefaultTokenEstimator.estimateFromUtf8ByteLength(5)).isEqualTo(2);
    assertThat(DefaultTokenEstimator.estimateFromUtf8ByteLength(4096)).isEqualTo(1024);
  }

  @Test
  void nonPositiveByteLengthEstimatesToZero() {
    assertThat(DefaultTokenEstimator.estimateFromUtf8ByteLength(-1)).isZero();
  }
}
