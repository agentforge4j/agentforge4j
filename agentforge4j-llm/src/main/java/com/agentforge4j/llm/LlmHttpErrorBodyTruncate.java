// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import org.apache.commons.lang3.StringUtils;

/**
 * Truncates HTTP error bodies before embedding them in exception messages or error logs.
 */
final class LlmHttpErrorBodyTruncate {

  private LlmHttpErrorBodyTruncate() {}

  static String truncateForEmbeddedMessage(String body, int maxChars) {
    String s = StringUtils.defaultString(body);
    if (maxChars <= 0) {
      return "";
    }
    return s.substring(0, Math.min(maxChars, s.length()));
  }
}
