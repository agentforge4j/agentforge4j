// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.LoopEvaluator;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.util.Validate;

/**
 * Iterates the blueprint body and, after each iteration, delegates to a {@link LoopEvaluator} that
 * asks a separate evaluator agent whether the loop should terminate.
 */
public final class EvaluatorLoopStrategy extends AbstractLoopStrategy {

  private static final System.Logger LOG = System.getLogger(EvaluatorLoopStrategy.class.getName());

  private final LoopEvaluator loopEvaluator;

  public EvaluatorLoopStrategy(StepSequenceExecutor stepSequenceExecutor,
      EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler,
      LoopEvaluator loopEvaluator) {
    super(stepSequenceExecutor, eventRecorder, maxIterationsHandler);
    this.loopEvaluator = Validate.notNull(loopEvaluator, "loopEvaluator must not be null");
  }

  @Override
  public LoopTerminationStrategy strategy() {
    return LoopTerminationStrategy.EVALUATOR;
  }

  @Override
  public ExecutionOutcome iterate(BlueprintDefinition blueprint,
      LoopConfig config,
      ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    String blueprintId = blueprint.blueprintId();
    int start = firstLoopIterationToRun(state, blueprintId);
    for (int iteration = start; iteration <= config.maxIterations(); iteration++) {
      ExecutionOutcome outcome = execute(blueprint, config, executionContext, iteration);
      if (outcome == ExecutionOutcome.PAUSED) {
        return outcome;
      }
      if (outcome == ExecutionOutcome.FAILED) {
        clearLoopIterationCursor(state, blueprintId);
        return outcome;
      }
      if (executionContext.getState().getStatus() == WorkflowStatus.CANCELLED) {
        clearLoopIterationCursor(state, blueprintId);
        return ExecutionOutcome.PAUSED;
      }
      if (shouldTerminate(config, executionContext, iteration)) {
        clearLoopIterationCursor(state, blueprintId);
        LOG.log(System.Logger.Level.INFO,
            "Loop terminated strategy={0}, iterations={1}, reason=EVALUATOR_SIGNAL",
            strategy(), iteration);
        return ExecutionOutcome.COMPLETED;
      }
    }
    ExecutionOutcome bounded = maxIterationsHandler.handle(blueprint, config, executionContext);
    if (bounded == ExecutionOutcome.FAILED) {
      clearLoopIterationCursor(state, blueprintId);
    }
    return bounded;
  }

  private boolean shouldTerminate(LoopConfig config, ExecutionContext executionContext,
      int iteration) {
    boolean termination = loopEvaluator.shouldTerminate(config.evaluatorAgentId(), iteration,
        executionContext);
    LOG.log(System.Logger.Level.DEBUG,
        "Loop iteration complete iteration={0}, terminationSignal={1}",
        iteration, termination);
    return termination;
  }

  private ExecutionOutcome execute(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, int iteration) {
    markLoopIterationStart(executionContext.getState(), blueprint.blueprintId(), iteration);
    LOG.log(System.Logger.Level.DEBUG,
        "Loop iteration start strategy={0}, iteration={1}, maxIterations={2}",
        strategy(), iteration, config.maxIterations());
    ExecutionOutcome outcome = executeIteration(blueprint, iteration, executionContext);
    return outcome;
  }
}
