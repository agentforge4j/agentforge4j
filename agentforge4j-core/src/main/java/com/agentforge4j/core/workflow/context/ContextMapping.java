package com.agentforge4j.core.workflow.context;

import java.util.List;

/**
 * Defines which context keys a step reads from and writes to. Instances are immutable.
 */
public record ContextMapping(List<String> inputKeys, List<String> outputKeys) {

  public ContextMapping {
    inputKeys = inputKeys != null ? List.copyOf(inputKeys) : List.of();
    outputKeys = outputKeys != null ? List.copyOf(outputKeys) : List.of();
  }

  /**
   * Factory method for a mapping with no input or output keys.
   *
   * @return empty context mapping
   */
  public static ContextMapping none() {
    return new ContextMapping(List.of(), List.of());
  }
}
