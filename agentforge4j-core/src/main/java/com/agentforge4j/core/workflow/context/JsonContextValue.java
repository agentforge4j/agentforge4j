package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

public record JsonContextValue(@JsonProperty(required = true) String json) implements ContextValue {
  public JsonContextValue {
    Validate.notBlank(json, "JsonContextValue json must not be blank");
  }
}
