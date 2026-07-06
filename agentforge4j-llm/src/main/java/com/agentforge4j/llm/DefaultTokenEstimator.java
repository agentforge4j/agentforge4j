// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.TokenEstimator;
import com.agentforge4j.util.Validate;
import java.nio.charset.StandardCharsets;

/**
 * Shipped OSS {@link TokenEstimator}: a conservative {@code ceil(utf8ByteLength / 4)} heuristic used
 * when the provider does not expose a tokenizer. This is the single implementation of the chars-per-token
 * estimate shared across the framework; provider prompt-cache support delegates to
 * {@link #estimateFromUtf8ByteLength(int)} rather than duplicating the formula.
 *
 * <p>Estimates feed advisory evidence only (segment-size thresholds, governance metadata) — never gates,
 * limits, or cost mapping.
 */
public final class DefaultTokenEstimator implements TokenEstimator {

  /**
   * UTF-8 bytes assumed per token by the default heuristic.
   */
  public static final double BYTES_PER_TOKEN = 4.0;

  /**
   * Estimates the token count of the given text from its UTF-8 byte length.
   *
   * @param text the text to estimate; must not be {@code null}
   *
   * @return the estimated token count; {@code 0} for empty text, otherwise {@code ceil(utf8Length / 4)}
   */
  @Override
  public int estimate(String text) {
    Validate.notNull(text, "text must not be null");
    return estimateFromUtf8ByteLength(text.getBytes(StandardCharsets.UTF_8).length);
  }

  /**
   * Estimates the token count for a segment of the given UTF-8 byte length using
   * {@link #BYTES_PER_TOKEN}. The byte-length entry point exists for callers that already work in
   * UTF-8 offset space (such as provider prompt-cache breakpoint selection) and must not re-encode.
   *
   * @param utf8ByteLength the segment length in UTF-8 bytes
   *
   * @return the estimated token count (at least {@code 1} when {@code utf8ByteLength} is positive,
   *         {@code 0} when it is not positive)
   */
  public static int estimateFromUtf8ByteLength(int utf8ByteLength) {
    if (utf8ByteLength <= 0) {
      return 0;
    }
    return (int) Math.ceil((double) utf8ByteLength / BYTES_PER_TOKEN);
  }
}
