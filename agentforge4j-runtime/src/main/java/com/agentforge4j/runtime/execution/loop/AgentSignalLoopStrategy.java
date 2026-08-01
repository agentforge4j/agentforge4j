// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;

/**
 * Iterates the blueprint body until an agent emits a {@code COMPLETE} command. The command is surfaced via
 * {@link ExecutionContext#isAgentCompletionSignalled()}, which the behaviour handler sets when a {@code COMPLETE} is
 * applied — the iteration's execution outcome stays {@code COMPLETED} so step gating and sequence continuation
 * elsewhere are unaffected. The flag reflects the last agent step of the iteration, so in a multi-step body the body's
 * final decision step controls termination.
 *
 * <p>The termination check also consults {@link com.agentforge4j.core.workflow.state.WorkflowState#isAgentSignalCompleted(String)},
 * the durable counterpart the behaviour handler persists alongside the transient flag: the transient flag is
 * always reset to {@code false} at the top of a redriven iteration (see {@link #iterate}), so a {@code COMPLETE}
 * signalled before a later step in the same iteration paused would otherwise be lost when that later step's resume
 * finishes the iteration — the signalling step itself has an output by then and is correctly skip-guarded, so it
 * never re-fires to re-set the transient flag. Reading the persisted marker closes that gap without re-invoking the
 * agent.
 *
 * <p>If {@code maxIterations} is reached without a completion signal the
 * {@link MaxIterationsHandler} decides whether to pause the run (await user) or fail it.
 */
public final class AgentSignalLoopStrategy extends AbstractLoopStrategy {

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
    return iterateUntilSignalled(blueprint, config, executionContext,
        iteration -> executionContext.isAgentCompletionSignalled()
            || executionContext.getState().isAgentSignalCompleted(blueprint.blueprintId()),
        "AGENT_SIGNAL");
  }
}
