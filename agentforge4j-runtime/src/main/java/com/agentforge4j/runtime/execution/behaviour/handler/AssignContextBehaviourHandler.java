// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.AssignContextBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;

/**
 * Writes an {@link AssignContextBehaviour}'s literal scalar value into the run context, re-stamping its provenance as
 * {@link ContextProvenance#SYSTEM_GENERATED} (the value originates from trusted workflow configuration, not from a
 * model). Deterministic; always completes.
 */
public final class AssignContextBehaviourHandler
    implements BehaviourHandler<AssignContextBehaviour> {

  private static final System.Logger LOG = System.getLogger(AssignContextBehaviourHandler.class.getName());

  private final EventRecorder eventRecorder;

  /**
   * Creates a handler.
   *
   * @param eventRecorder audit sink for the context-update event
   */
  public AssignContextBehaviourHandler(EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  @Override
  public Class<AssignContextBehaviour> behaviourType() {
    return AssignContextBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step, AssignContextBehaviour behaviour,
      ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    LOG.log(System.Logger.Level.DEBUG, "Assign context stepId={0}, contextKey={1}",
        step.stepId(), behaviour.contextKey());
    state.putContextValue(behaviour.contextKey(),
        behaviour.value().withProvenance(ContextProvenance.SYSTEM_GENERATED));
    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.CONTEXT_UPDATED,
        "assigned context key: %s".formatted(behaviour.contextKey()), "runtime");
    return ExecutionOutcome.COMPLETED;
  }
}
