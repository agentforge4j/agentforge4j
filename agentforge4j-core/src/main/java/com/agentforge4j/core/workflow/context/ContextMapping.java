package com.agentforge4j.core.workflow.context;

import java.util.List;

public record ContextMapping(List<String> inputKeys, List<String> outputKeys) {
  public ContextMapping {
    inputKeys = inputKeys != null ? List.copyOf(inputKeys) : List.of();
    outputKeys = outputKeys != null ? List.copyOf(outputKeys) : List.of();
  }

  public static ContextMapping none() {
    return new ContextMapping(List.of(), List.of());
  }
}
