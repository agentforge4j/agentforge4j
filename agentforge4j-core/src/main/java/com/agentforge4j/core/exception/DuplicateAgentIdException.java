package com.agentforge4j.core.exception;

import java.util.List;

/**
 * Thrown when the same agent id would be registered from more than one source.
 */
public final class DuplicateAgentIdException extends RuntimeException {

  public DuplicateAgentIdException(List<String> details) {
    super(formatMessage(details));
  }

  private static String formatMessage(List<String> details) {
    StringBuilder sb = new StringBuilder("Duplicate agent id(s):");
    for (String line : details) {
      sb.append(System.lineSeparator()).append("  - ").append(line);
    }
    return sb.toString();
  }
}
