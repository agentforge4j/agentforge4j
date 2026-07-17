// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;

/**
 * The read-only view of a run a {@link LoopEvaluator} needs to decide whether an
 * {@link com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy#EVALUATOR} loop should
 * terminate.
 *
 * <p>A narrow, exported view rather than the runtime's internal per-drive execution context (which
 * is not exported, and carries mutation methods no evaluator needs): every module-path consumer
 * that implements {@link LoopEvaluator} can construct or receive this type.
 *
 * @param state             the live state of the current run; never {@code null}
 * @param activeWorkflowId  the id of the innermost workflow currently executing (the root workflow
 *                          id when no nested workflow has been entered); never blank
 */
public record LoopEvaluationContext(WorkflowState state, String activeWorkflowId) {

  public LoopEvaluationContext {
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(activeWorkflowId, "activeWorkflowId must not be blank");
  }
}
