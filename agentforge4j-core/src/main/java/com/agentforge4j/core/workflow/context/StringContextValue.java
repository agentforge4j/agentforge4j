package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * String-typed context value.
 */
public record StringContextValue(@JsonProperty(required = true) String value) implements ContextValue {
  public StringContextValue {
    Validate.notBlank(value, "StringContextValue value must not be blank");
  }
}
