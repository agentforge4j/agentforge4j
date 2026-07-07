// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import org.apache.commons.lang3.StringUtils;

/**
 * Truncates HTTP response/error bodies before embedding them in exception messages or ERROR-level
 * logs, so large or sensitive provider payloads never leak into logs wholesale. The full,
 * untruncated body remains available to callers for DEBUG-level logging.
 * <p>
 * Shared across every {@code agentforge4j-llm-*} provider module (all depend on
 * {@code agentforge4j-llm}), so the same truncation policy applies whether the raw body is
 * embedded because of a non-2xx HTTP status or because a 2xx body failed to parse/validate.
 */
public final class LlmHttpErrorBodyTruncate {

  /**
   * Default maximum number of characters retained when a response body is embedded in an
   * exception message.
   */
  public static final int DEFAULT_MAX_CHARS = 500;

  private LlmHttpErrorBodyTruncate() {}

  /**
   * Truncates {@code body} to at most {@code maxChars} characters, treating {@code null} as an
   * empty string.
   *
   * @param body     the raw body to truncate; {@code null} is treated as empty
   * @param maxChars the maximum number of characters to retain; non-positive values return an
   *                 empty string
   *
   * @return the truncated body
   */
  public static String truncateForEmbeddedMessage(String body, int maxChars) {
    String s = StringUtils.defaultString(body);
    if (maxChars <= 0) {
      return "";
    }
    return s.substring(0, Math.min(maxChars, s.length()));
  }

  /**
   * Truncates {@code body} to {@link #DEFAULT_MAX_CHARS} characters, treating {@code null} as an
   * empty string.
   *
   * @param body the raw body to truncate; {@code null} is treated as empty
   *
   * @return the truncated body
   */
  public static String truncateForEmbeddedMessage(String body) {
    return truncateForEmbeddedMessage(body, DEFAULT_MAX_CHARS);
  }
}
