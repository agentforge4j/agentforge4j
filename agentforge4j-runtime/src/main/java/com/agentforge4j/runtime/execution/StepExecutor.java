package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Executes a {@link StepDefinition} by locating the right {@link BehaviourHandler} for the step's
 * behaviour type and delegating.
 *
 * <p>Responsible for emitting {@code STEP_STARTED}, {@code STEP_COMPLETED}, and
 * {@code STEP_FAILED} events, and for updating the run's current step pointer.
 */
public final class StepExecutor {

  private static final System.Logger LOG = System.getLogger(StepExecutor.class.getName());

  private final Map<Class<? extends StepBehaviour>, BehaviourHandler<? extends StepBehaviour>> handlersByType;
  private final EventRecorder eventRecorder;
  private final Clock clock;
  private final TransitionGate transitionGate;

  public StepExecutor(Collection<BehaviourHandler<? extends StepBehaviour>> handlers,
      EventRecorder eventRecorder,
      Clock clock,
      TransitionGate transitionGate) {
    Validate.notEmpty(handlers, "StepExecutor requires at least one BehaviourHandler");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
    this.transitionGate = Validate.notNull(transitionGate, "transitionGate must not be null");
    this.handlersByType = indexHandlers(handlers);
  }

  public ExecutionOutcome execute(StepDefinition step, ExecutionContext executionContext) {
    Validate.notNull(step, "step must not be null");
    WorkflowState state = getWorkflowState(step, executionContext);

    BehaviourHandler<StepBehaviour> handler = lookupHandler(step.behaviour());
    try {
      ExecutionOutcome outcome = handler.handle(step, step.behaviour(), executionContext);
      return afterHandle(step, state, outcome);
    } catch (RuntimeException e) {
      markFailed(state, step, e);
      LOG.log(System.Logger.Level.ERROR, "STEP_FAILED runId=" + state.getRunId()
          + ", stepId=" + step.stepId() + ", message=" + e.getMessage(), e);
      throw e;
    }
  }

  private WorkflowState getWorkflowState(StepDefinition step, ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    state.setCurrentStepId(step.stepId());
    state.setLastUpdatedAt(clock.instant());

    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.STEP_STARTED, null,
        "runtime");
    LOG.log(System.Logger.Level.INFO, "STEP_STARTED runId={0}, stepId={1}, behaviourType={2}",
        state.getRunId(), step.stepId(), step.behaviour().getClass().getSimpleName());
    return state;
  }

  private ExecutionOutcome afterHandle(StepDefinition step, WorkflowState state,
      ExecutionOutcome outcome) {
    Validate.notNull(outcome,
        "BehaviourHandler returned null outcome for step: %s".formatted(step.stepId()));
    state.setLastUpdatedAt(clock.instant());
    if (outcome == ExecutionOutcome.COMPLETED || outcome == ExecutionOutcome.COMPLETED_SIGNAL) {
      eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.STEP_COMPLETED, null,
          "runtime");
      LOG.log(System.Logger.Level.INFO, "STEP_COMPLETED runId={0}, stepId={1}",
          state.getRunId(), step.stepId());
    }
    if (outcome == ExecutionOutcome.COMPLETED && transitionGate.suspendIfGated(step, state)) {
      LOG.log(System.Logger.Level.INFO, "STEP gated, suspending runId={0}, stepId={1}, status={2}",
          state.getRunId(), step.stepId(), state.getStatus());
      return ExecutionOutcome.PAUSED;
    }
    return outcome;
  }

  private void markFailed(WorkflowState state, StepDefinition step, RuntimeException cause) {
    String payload = "Step '%s' failed: %s".formatted(step.stepId(), cause.getMessage());
    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.STEP_FAILED, payload,
        "runtime");
  }

  @SuppressWarnings("unchecked")
  private BehaviourHandler<StepBehaviour> lookupHandler(StepBehaviour behaviour) {
    return (BehaviourHandler<StepBehaviour>) Validate.notNull(
        handlersByType.get(behaviour.getClass()),
        "No BehaviourHandler registered for behaviour type: " + behaviour.getClass().getName());
  }

  private static Map<Class<? extends StepBehaviour>, BehaviourHandler<? extends StepBehaviour>> indexHandlers(
      Collection<BehaviourHandler<? extends StepBehaviour>> handlers) {
    Map<Class<? extends StepBehaviour>, BehaviourHandler<? extends StepBehaviour>> byType = new HashMap<>();
    for (BehaviourHandler<? extends StepBehaviour> handler : handlers) {
      Validate.notNull(handler, "BehaviourHandler collection must not contain null entries");
      BehaviourHandler<? extends StepBehaviour> existing = byType.putIfAbsent(
          handler.behaviourType(), handler);
      Validate.isTrue(existing == null, () -> new IllegalStateException(
          "Duplicate BehaviourHandler for behaviour type: %s (%s vs %s)"
              .formatted(handler.behaviourType().getName(), existing.getClass().getName(),
                  handler.getClass().getName())));
    }
    return Map.copyOf(byType);
  }
}
