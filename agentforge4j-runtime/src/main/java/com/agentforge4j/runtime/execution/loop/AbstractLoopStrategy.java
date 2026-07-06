// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
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

  private static final System.Logger LOG = System.getLogger(AbstractLoopStrategy.class.getName());

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
   * First 1-based iteration index to run for a resumed loop. Stored cursor {@code 0} means start
   * at iteration {@code 1}; a positive cursor is the in-progress iteration to re-run after a
   * pause.
   */
  protected static int firstLoopIterationToRun(WorkflowState state, String blueprintId) {
    int cursor = state.getLoopIterationCursor(blueprintId);
    return Math.max(cursor, 1);
  }

  /**
   * Marks {@code iteration} as the loop iteration now starting for {@code blueprintId}, and — when
   * this is a genuinely new iteration rather than a resume into one already in progress — clears
   * the previous iteration's body step outputs/uids so {@code StepSequenceExecutor}'s resume-skip
   * guard does not mistake them for this iteration's own (CR-1: previously, nothing cleared body
   * outputs between iterations, so iterations 2..N of every loop silently skipped their entire
   * body).
   *
   * <p>The previous iteration's outputs are identified by the uid at which its body began
   * ({@link WorkflowState#getLoopIterationBodyStartUid(String)}), recorded the last time this
   * method genuinely started a new iteration. A resume into a paused iteration is detected by the
   * persisted cursor already equalling {@code iteration}: in that case nothing is cleared, so
   * steps completed before the pause are still skipped correctly on re-entry.
   */
  protected static void markLoopIterationStart(ExecutionContext executionContext,
      String blueprintId, int iteration) {
    WorkflowState state = executionContext.getState();
    if (state.getLoopIterationCursor(blueprintId) != iteration) {
      int previousBodyStartUid = state.getLoopIterationBodyStartUid(blueprintId);
      if (previousBodyStartUid > 0) {
        state.clearEntriesFromUid(previousBodyStartUid);
      }
      state.setLoopIterationBodyStartUid(blueprintId, executionContext.peekNextStepSequenceUid());
    }
    state.setLoopIterationCursor(blueprintId, iteration);
  }

  protected static void clearLoopIterationCursor(WorkflowState state, String blueprintId) {
    state.clearLoopIterationCursor(blueprintId);
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

  /**
   * Runs a single bounded iteration of the body: advances the loop cursor, logs, and delegates to
   * {@link #executeIteration}. Shared by the strategies so the per-iteration bookkeeping lives in
   * one place.
   */
  protected ExecutionOutcome runIteration(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, int iteration) {
    markLoopIterationStart(executionContext, blueprint.blueprintId(), iteration);
    LOG.log(System.Logger.Level.DEBUG,
        "Loop iteration start strategy={0}, iteration={1}, maxIterations={2}",
        strategy(), iteration, config.maxIterations());
    ExecutionOutcome outcome = executeIteration(blueprint, iteration, executionContext);
    LOG.log(System.Logger.Level.DEBUG, "Loop iteration complete strategy={0}, iteration={1}, outcome={2}",
        strategy(), iteration, outcome);
    return outcome;
  }

  /**
   * Runs the body up to {@code maxIterations}, ending early when {@code terminationCheck} signals
   * after an iteration. Returns immediately on a paused iteration (cursor preserved for resume),
   * clears the cursor and returns on failure or cancellation, and — on a clean termination signal —
   * clears the cursor, logs the {@code terminationReason}, and completes. When the iteration ceiling
   * is reached without a signal, {@link MaxIterationsHandler} decides the outcome.
   *
   * <p>Shared by strategies whose termination is decided per iteration (agent signal, evaluator).
   * The per-iteration agent completion signal is cleared before each iteration so the check observes
   * only the current iteration's body.
   */
  protected ExecutionOutcome iterateUntilSignalled(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, IterationTerminationCheck terminationCheck,
      String terminationReason) {
    WorkflowState state = executionContext.getState();
    String blueprintId = blueprint.blueprintId();
    int start = firstLoopIterationToRun(state, blueprintId);
    for (int iteration = start; iteration <= config.maxIterations(); iteration++) {
      executionContext.setAgentCompletionSignalled(false);
      ExecutionOutcome outcome = runIteration(blueprint, config, executionContext, iteration);
      if (outcome == ExecutionOutcome.PAUSED) {
        return outcome;
      }
      if (outcome == ExecutionOutcome.FAILED) {
        clearLoopIterationCursor(state, blueprintId);
        return outcome;
      }
      if (state.getStatus() == WorkflowStatus.CANCELLED) {
        clearLoopIterationCursor(state, blueprintId);
        return ExecutionOutcome.PAUSED;
      }
      if (terminationCheck.shouldTerminate(iteration)) {
        clearLoopIterationCursor(state, blueprintId);
        LOG.log(System.Logger.Level.INFO,
            "Loop terminated strategy={0}, iterations={1}, reason={2}",
            strategy(), iteration, terminationReason);
        return ExecutionOutcome.COMPLETED;
      }
    }
    return handleMaxIterations(blueprint, config, executionContext, state, blueprintId);
  }

  private ExecutionOutcome handleMaxIterations(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, WorkflowState state, String blueprintId) {
    ExecutionOutcome bounded = maxIterationsHandler.handle(blueprint, config, executionContext);
    if (bounded == ExecutionOutcome.FAILED) {
      clearLoopIterationCursor(state, blueprintId);
    }
    return bounded;
  }

  /**
   * Decides, after an iteration, whether a per-iteration loop should terminate cleanly.
   */
  @FunctionalInterface
  protected interface IterationTerminationCheck {

    boolean shouldTerminate(int iteration);
  }
}
