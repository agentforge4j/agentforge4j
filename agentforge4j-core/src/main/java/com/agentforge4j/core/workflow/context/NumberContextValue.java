package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NumberContextValue(@JsonProperty(required = true) Number value) implements ContextValue {
  public NumberContextValue {
    Validate.notNull(value, "NumberContextValue value must not be null");
  }
}
