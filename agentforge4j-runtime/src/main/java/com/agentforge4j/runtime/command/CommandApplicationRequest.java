// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.util.Validate;

/**
 * Immutable input bundle passed to {@link CommandHandler#apply(com.agentforge4j.core.command.LlmCommand, CommandApplicationRequest)}.
 *
 * @param state                    workflow state mutated by handlers
 * @param contextMapping           allowed context output keys for the active agent
 * @param agentId                  logical agent applying commands
 * @param currentStepUid           monotonic step instance id used when tagging context keys written
 *                                 during command application
 * @param step                     the step whose commands are being applied
 * @param enclosingWorkflow        the workflow definition enclosing {@code step} (root, or the active
 *                                 nested workflow), used to resolve context sources by reference
 * @param priorRequestContextExpansions number of context expansions requested by earlier
 *                                 {@code RequestContextCommand} instances in this batch (each
 *                                 requested selector counts as one expansion; 0 for the first such
 *                                 command and for any other command type); used to enforce the
 *                                 per-invocation expansion limit across the whole batch
 */
public record CommandApplicationRequest(
    WorkflowState state,
    ContextMapping contextMapping,
    String agentId,
    int currentStepUid,
    StepDefinition step,
    WorkflowDefinition enclosingWorkflow,
    int priorRequestContextExpansions) {

  /**
   * Validates required fields.
   */
  public CommandApplicationRequest {
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(agentId, "agentId must not be blank");
    Validate.notNull(contextMapping, "contextMapping must not be null");
    Validate.notNull(step, "step must not be null");
    Validate.notNull(enclosingWorkflow, "enclosingWorkflow must not be null");
    Validate.isNotNegative(priorRequestContextExpansions,
        "priorRequestContextExpansions must not be negative");
  }
}
