package com.agentforge4j.core.command;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request execution of a shell command. The runtime MAY reject this command if the embedding
 * application has not registered a shell executor.
 */
public record RunCommandCommand(@JsonProperty(required = true) String command) implements
    LlmCommand {

  public RunCommandCommand {
    Validate.notBlank(command, "RunCommandCommand command must not be blank");
  }
}
