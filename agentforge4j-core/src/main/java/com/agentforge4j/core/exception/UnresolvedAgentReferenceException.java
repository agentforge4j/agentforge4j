package com.agentforge4j.core.exception;

import java.util.List;

/**
 * Thrown when loaded workflows reference agent ids that are not present in the merged agent
 * catalog. The message lists every missing reference.
 */
public final class UnresolvedAgentReferenceException extends RuntimeException {

  public UnresolvedAgentReferenceException(List<String> details) {
    super(formatMessage(details));
  }

  private static String formatMessage(List<String> details) {
    StringBuilder sb = new StringBuilder("Unresolved agent reference(s):");
    for (String line : details) {
      sb.append(System.lineSeparator()).append("  - ").append(line);
    }
    return sb.toString();
  }
}
