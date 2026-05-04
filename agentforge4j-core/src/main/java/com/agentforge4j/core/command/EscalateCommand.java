package com.agentforge4j.core.command;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Signal that the current step cannot proceed without human intervention.
 * The runtime transitions the run to {@code WorkflowStatus.AWAITING_APPROVAL}.
 */
public record EscalateCommand(@JsonProperty(required = true) String reason) implements LlmCommand {
  public EscalateCommand {
    Validate.notBlank(reason, "EscalateCommand reason must not be blank");
  }
}
