// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.TokenEstimator;
import com.agentforge4j.util.Validate;
import java.nio.charset.StandardCharsets;

/**
 * Shipped OSS {@link TokenEstimator}: the framework's default heuristic applied to text. Encodes the
 * text as UTF-8 and defers the arithmetic to {@link Utf8TokenEstimate}, which is what the provider
 * prompt-cache paths also use — the formula lives in exactly one place, and this class adds only the
 * text-to-bytes step and the SPI shape around it.
 *
 * <p>Estimates feed advisory evidence only (segment-size thresholds, governance metadata) — never gates
 * or limits.
 */
public final class DefaultTokenEstimator implements TokenEstimator {

  /**
   * Estimates the token count of the given text from its UTF-8 byte length.
   *
   * @param text the text to estimate; must not be {@code null}
   *
   * @return the estimated token count; {@code 0} for empty text
   */
  @Override
  public int estimate(String text) {
    Validate.notNull(text, "text must not be null");
    return Utf8TokenEstimate.fromUtf8ByteLength(text.getBytes(StandardCharsets.UTF_8).length);
  }
}
