package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;

/**
 * Iterates the blueprint body until an agent emits a {@code COMPLETE} command — surfaced to this
 * strategy as {@link ExecutionOutcome#COMPLETED_SIGNAL}.
 *
 * <p>If {@code maxIterations} is reached without a signal the
 * {@link MaxIterationsHandler} decides whether to pause the run or fail it.
 */
public final class AgentSignalLoopStrategy extends AbstractLoopStrategy {

  private static final System.Logger LOG = System.getLogger(
      AgentSignalLoopStrategy.class.getName());

  public AgentSignalLoopStrategy(StepSequenceExecutor stepSequenceExecutor,
      EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler) {
    super(stepSequenceExecutor, eventRecorder, maxIterationsHandler);
  }

  @Override
  public LoopTerminationStrategy strategy() {
    return LoopTerminationStrategy.AGENT_SIGNAL;
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
      switch (outcome) {
        case COMPLETED_SIGNAL -> {
          clearLoopIterationCursor(state, blueprintId);
          LOG.log(System.Logger.Level.INFO,
              "Loop terminated strategy={0}, iterations={1}, reason=AGENT_SIGNAL",
              strategy(), iteration);
          return ExecutionOutcome.COMPLETED;
        }
        case COMPLETED -> {
          if (executionContext.getState().getStatus() == WorkflowStatus.CANCELLED) {
            clearLoopIterationCursor(state, blueprintId);
            return ExecutionOutcome.PAUSED;
          }
        }
        case PAUSED -> {
          return outcome;
        }
        case FAILED -> {
          clearLoopIterationCursor(state, blueprintId);
          return outcome;
        }
      }
    }
    ExecutionOutcome bounded = maxIterationsHandler.handle(blueprint, config, executionContext);
    if (bounded == ExecutionOutcome.FAILED) {
      clearLoopIterationCursor(state, blueprintId);
    }
    return bounded;
  }

  private ExecutionOutcome execute(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, int iteration) {
    markLoopIterationStart(executionContext.getState(), blueprint.blueprintId(), iteration);
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
