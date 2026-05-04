package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ContextValueList(
    @JsonProperty(required = true)
    List<ContextValue> values) implements ContextValue {
  public ContextValueList {
    Validate.notNull(values,"ContextValueList values must not be null");
    values = List.copyOf(values);
  }
}
