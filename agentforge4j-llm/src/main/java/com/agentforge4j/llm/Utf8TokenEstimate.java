// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

/**
 * The framework's default token-count heuristic: {@code ceil(utf8ByteLength / 4)}. Used where a
 * token count is needed but the provider exposes no tokenizer — currently the Anthropic-style
 * prompt-cache breakpoint decision in the Claude and Bedrock adapters, which compare a segment's
 * estimated size against a per-model minimum before marking it cacheable.
 *
 * <p>Deliberately byte-length based rather than text based. Callers that select cache breakpoints
 * already work in UTF-8 offset space, and re-encoding a prompt they have already encoded would be
 * both wasteful and a second chance to disagree about what "length" means. A text overload is not
 * offered because nothing needs one.
 *
 * <p>The estimate is advisory evidence, never a gate or a limit: it decides whether caching is
 * worth attempting, and being wrong costs a cache miss rather than a wrong answer.
 */
public final class Utf8TokenEstimate {

  /**
   * UTF-8 bytes assumed per token. Conservative on purpose — over-estimating tokens would mark
   * segments cacheable that a provider then rejects as too short. Private: callers want the
   * estimate, not the divisor, and exposing it would invite a second implementation of the very
   * formula this class exists to hold.
   */
  private static final double BYTES_PER_TOKEN = 4.0;

  private Utf8TokenEstimate() {
  }

  /**
   * Estimates the token count of a segment of the given UTF-8 byte length.
   *
   * @param utf8ByteLength the segment length in UTF-8 bytes
   *
   * @return {@code 0} when {@code utf8ByteLength} is not positive, otherwise
   *         {@code ceil(utf8ByteLength / 4)} — which is at least {@code 1}
   */
  public static int fromUtf8ByteLength(int utf8ByteLength) {
    if (utf8ByteLength <= 0) {
      return 0;
    }
    return (int) Math.ceil((double) utf8ByteLength / BYTES_PER_TOKEN);
  }
}
