package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.util.Validate;

/**
 * Shared base for loop strategies that delegate iteration bodies to {@link StepSequenceExecutor}
 * and record iteration events.
 */
abstract class AbstractLoopStrategy implements LoopStrategy {

  protected final StepSequenceExecutor stepSequenceExecutor;
  protected final EventRecorder eventRecorder;
  protected final MaxIterationsHandler maxIterationsHandler;

  protected AbstractLoopStrategy(StepSequenceExecutor stepSequenceExecutor,
      EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler) {
    this.stepSequenceExecutor = Validate.notNull(stepSequenceExecutor,
        "stepSequenceExecutor must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.maxIterationsHandler = Validate.notNull(maxIterationsHandler,
        "maxIterationsHandler must not be null");
  }

  /**
   * Execute a single iteration of the blueprint body, emitting start and complete loop events.
   */
  protected ExecutionOutcome executeIteration(BlueprintDefinition blueprint,
      int iteration,
      ExecutionContext executionContext) {
    if (executionContext.getState().getStatus() == WorkflowStatus.CANCELLED) {
      return ExecutionOutcome.PAUSED;
    }
    String runId = executionContext.getState().getRunId();
    String payload = "iteration=%d".formatted(iteration);
    eventRecorder.record(runId, blueprint.blueprintId(),
        WorkflowEventType.LOOP_ITERATION_STARTED, payload, "runtime");
    ExecutionOutcome outcome = stepSequenceExecutor.executeAll(blueprint.steps(), executionContext);
    eventRecorder.record(runId, blueprint.blueprintId(),
        WorkflowEventType.LOOP_ITERATION_COMPLETED, payload, "runtime");
    return outcome;
  }
}
