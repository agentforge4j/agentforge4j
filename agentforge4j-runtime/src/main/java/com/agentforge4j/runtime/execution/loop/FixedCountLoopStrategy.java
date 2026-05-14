package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;

/**
 * Executes the blueprint body exactly {@code maxIterations} times — or until an iteration pauses or
 * fails.
 */
public final class FixedCountLoopStrategy extends AbstractLoopStrategy {

  private static final System.Logger LOG = System.getLogger(FixedCountLoopStrategy.class.getName());

  public FixedCountLoopStrategy(StepSequenceExecutor stepSequenceExecutor,
      EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler) {
    super(stepSequenceExecutor, eventRecorder, maxIterationsHandler);
  }

  @Override
  public LoopTerminationStrategy strategy() {
    return LoopTerminationStrategy.FIXED_COUNT;
  }

  @Override
  public ExecutionOutcome iterate(BlueprintDefinition blueprint,
      LoopConfig config,
      ExecutionContext executionContext) {
    for (int iteration = 1; iteration <= config.maxIterations(); iteration++) {
      ExecutionOutcome outcome = execute(blueprint, config, executionContext, iteration);
      if (outcome == ExecutionOutcome.PAUSED || outcome == ExecutionOutcome.FAILED) {
        return outcome;
      }
      if (executionContext.getState().getStatus() == WorkflowStatus.CANCELLED) {
        return ExecutionOutcome.PAUSED;
      }
      // COMPLETED_SIGNAL is ignored for fixed count — we run to N regardless.
    }
    LOG.log(System.Logger.Level.INFO,
        "Loop terminated strategy={0}, iterations={1}, reason=FIXED_COUNT_REACHED",
        strategy(), config.maxIterations());
    return ExecutionOutcome.COMPLETED;
  }

  private ExecutionOutcome execute(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, int iteration) {
    LOG.log(System.Logger.Level.DEBUG,
        "Loop iteration start strategy={0}, iteration={1}, maxIterations={2}",
        strategy(), iteration, config.maxIterations());
    ExecutionOutcome outcome = executeIteration(blueprint, iteration, executionContext);
    LOG.log(System.Logger.Level.DEBUG,
        "Loop iteration complete iteration={0}, terminationSignal={1}",
        iteration, outcome == ExecutionOutcome.COMPLETED_SIGNAL);
    return outcome;
  }
}
