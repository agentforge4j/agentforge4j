// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.support;

import com.agentforge4j.llm.api.TokenEstimator;

/**
 * Test-only {@link TokenEstimator} that always returns a fixed count, regardless of the text it is
 * given. Discovered via {@code ServiceLoader} from a test-scope {@code META-INF/services}
 * registration, proving the positive discovery path (a real registered {@link TokenEstimator} is
 * actually returned by {@code TokenEstimatorResolver.resolve()}) that
 * {@code TokenEstimatorResolverTest} alone does not exercise.
 */
public final class FixedCountTokenEstimator implements TokenEstimator {

  /** The token count this estimator always returns. */
  public static final int FIXED_COUNT = 42;

  @Override
  public int estimate(String text) {
    return FIXED_COUNT;
  }
}
