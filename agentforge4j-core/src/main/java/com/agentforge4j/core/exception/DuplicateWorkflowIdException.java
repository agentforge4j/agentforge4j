// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.exception;

import java.util.List;

/**
 * Thrown when the same workflow id would be registered from more than one source.
 */
public final class DuplicateWorkflowIdException extends RuntimeException {

  public DuplicateWorkflowIdException(List<String> details) {
    super(formatMessage(details));
  }

  private static String formatMessage(List<String> details) {
    StringBuilder sb = new StringBuilder("Duplicate workflow id(s):");
    for (String line : details) {
      sb.append(System.lineSeparator()).append("  - ").append(line);
    }
    return sb.toString();
  }
}
