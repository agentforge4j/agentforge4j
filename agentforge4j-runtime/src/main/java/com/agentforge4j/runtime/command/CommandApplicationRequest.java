// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command;

import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;

/**
 * Immutable input bundle passed to {@link CommandHandler#apply(com.agentforge4j.core.command.LlmCommand, CommandApplicationRequest)}.
 *
 * @param state            workflow state mutated by handlers
 * @param contextMapping   allowed context output keys for the active agent
 * @param agentId          logical agent applying commands
 * @param currentStepUid   monotonic step instance id used when tagging context keys written during
 *                         command application
 */
public record CommandApplicationRequest(
    WorkflowState state,
    ContextMapping contextMapping,
    String agentId,
    int currentStepUid) {

  /**
   * Validates {@code state}, {@code contextMapping}, and {@code agentId}.
   */
  public CommandApplicationRequest {
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(agentId, "agentId must not be blank");
    Validate.notNull(contextMapping, "contextMapping must not be null");
  }
}
