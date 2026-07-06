// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.api;

/**
 * Estimates the token count of a piece of text when the provider does not expose a tokenizer. A
 * provider-neutral heuristic used only for advisory evidence — segment-size thresholds and governance
 * metadata — never for gates, limits, or billing.
 *
 * <p>The shipped default ({@code DefaultTokenEstimator} in {@code agentforge4j.llm}) is a conservative
 * bytes-per-token heuristic. Provider modules may supply a more accurate implementation; consumers
 * resolve one and fall back to the default when none is registered.
 */
@FunctionalInterface
public interface TokenEstimator {

  /**
   * Estimates the number of tokens the given text would occupy.
   *
   * @param text the text to estimate; must not be {@code null} (empty text estimates to {@code 0})
   *
   * @return the estimated token count; never negative
   */
  int estimate(String text);
}
