package com.agentforge4j.runtime.command;

import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;

public record CommandApplicationRequest(
    WorkflowState state,
    ContextMapping contextMapping,
    String agentId,
    int currentStepUid) {

  public CommandApplicationRequest {
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(agentId, "agentId must not be blank");
    Validate.notNull(contextMapping, "contextMapping must not be null");
  }
}
