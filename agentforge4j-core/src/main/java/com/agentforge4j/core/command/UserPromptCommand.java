// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Surface a message to the user. When {@code responseRequired} is true, the runtime pauses
 * execution and waits for user input.
 */
public record UserPromptCommand(
    @JsonProperty(required = true)
    String message,
    boolean responseRequired) implements LlmCommand {

  public UserPromptCommand {
    Validate.notBlank(message, "UserPromptCommand message must not be blank");
  }
}
