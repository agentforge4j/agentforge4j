// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers what this class adds over the shared heuristic: encoding text to UTF-8 before estimating,
 * and rejecting null. The ceiling arithmetic itself belongs to {@code Utf8TokenEstimateTest} and is
 * not restated here.
 */
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

}
