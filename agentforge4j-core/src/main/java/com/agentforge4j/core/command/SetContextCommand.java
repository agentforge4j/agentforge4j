// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Write a typed value into the shared workflow context under the given key.
 *
 * <p>The runtime resolves the key against {@code ContextMapping.outputKeys} of the
 * emitting step to decide whether the write is permitted.
 */
public record SetContextCommand(
    @JsonProperty(required = true)
    String key,
    @JsonProperty(required = true)
    ContextValue value) implements LlmCommand {

  public SetContextCommand {
    Validate.notBlank(key, "SetContextCommand key must not be blank");
    Validate.notNull(value, "SetContextCommand value must not be null for key: %s".formatted(key));
  }
}
