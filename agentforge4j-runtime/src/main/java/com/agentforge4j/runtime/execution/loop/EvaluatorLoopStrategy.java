// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.LoopEvaluationContext;
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
    return iterateUntilSignalled(blueprint, config, executionContext,
        iteration -> shouldTerminate(config, executionContext, iteration), "EVALUATOR_SIGNAL");
  }

  private boolean shouldTerminate(LoopConfig config, ExecutionContext executionContext,
      int iteration) {
    LoopEvaluationContext context = new LoopEvaluationContext(executionContext.getState(),
        executionContext.getActiveWorkflowId());
    boolean termination = loopEvaluator.shouldTerminate(config.evaluatorAgentId(), iteration,
        context);
    LOG.log(System.Logger.Level.DEBUG,
        "Loop iteration complete iteration={0}, terminationSignal={1}",
        iteration, termination);
    return termination;
  }
}
