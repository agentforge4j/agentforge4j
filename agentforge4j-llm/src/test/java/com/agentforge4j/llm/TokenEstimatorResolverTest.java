// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.TokenEstimator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEstimatorResolverTest {

  @Test
  void fallsBackToDefaultTokenEstimatorWhenNoneRegistered() {
    // No provider module on this test's module/class path registers a TokenEstimator, so resolution
    // falls back to the shipped default.
    TokenEstimator estimator = TokenEstimatorResolver.resolve();

    assertThat(estimator).isInstanceOf(DefaultTokenEstimator.class);
    assertThat(estimator.estimate("xxxx")).isEqualTo(1);
  }
}
