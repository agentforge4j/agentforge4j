// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import org.apache.commons.lang3.StringUtils;

/**
 * Produces user- and event-safe failure messages from throwables.
 */
final class FailureSanitiser {

  String sanitiseFailureReason(String failedStepId, RuntimeException throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    String base = StringUtils.isBlank(message) ? "Unexpected runtime error" : message.strip();
    return StringUtils.isBlank(failedStepId) ? base
        : "Step '%s' failed: %s".formatted(failedStepId, base);
  }

  /**
   * Short failure text for workflow events — no stack trace or type names.
   */
  String safeFailureReason(Throwable throwable) {
    if (throwable == null) {
      return "Unexpected runtime error";
    }
    String message = throwable.getMessage();
    if (StringUtils.isBlank(message)) {
      return "Unexpected runtime error";
    }
    String stripped = message.strip();
    return stripped.substring(0, Math.min(500, stripped.length()));
  }
}
