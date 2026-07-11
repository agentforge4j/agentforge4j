// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.spi.governance.WasteSignalPolicy;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Executes the blueprint body exactly {@code maxIterations} times — or until an iteration pauses or
 * fails.
 */
public final class FixedCountLoopStrategy extends AbstractLoopStrategy {

  private static final System.Logger LOG = System.getLogger(FixedCountLoopStrategy.class.getName());

  public FixedCountLoopStrategy(StepSequenceExecutor stepSequenceExecutor,
      EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler,
      ObjectMapper objectMapper,
      WasteSignalPolicy wasteSignalPolicy) {
    super(stepSequenceExecutor, eventRecorder, maxIterationsHandler, objectMapper,
        wasteSignalPolicy);
  }

  @Override
  public LoopTerminationStrategy strategy() {
    return LoopTerminationStrategy.FIXED_COUNT;
  }

  @Override
  public ExecutionOutcome iterate(BlueprintDefinition blueprint,
      LoopConfig config,
      ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    String blueprintId = blueprint.blueprintId();
    int start = firstLoopIterationToRun(state, blueprintId);
    for (int iteration = start; iteration <= config.maxIterations(); iteration++) {
      ExecutionOutcome outcome = runIteration(blueprint, config, executionContext, iteration);
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
      // A body-level agent completion signal is ignored for fixed count — we run to N regardless.
    }
    clearLoopIterationCursor(state, blueprintId);
    LOG.log(System.Logger.Level.INFO,
        "Loop terminated strategy={0}, iterations={1}, reason=FIXED_COUNT_REACHED",
        strategy(), config.maxIterations());
    return ExecutionOutcome.COMPLETED;
  }
}
