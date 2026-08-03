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
   * the previous iteration's body step outputs, execution uids, and nested completed-loop markers
   * (via {@link WorkflowState#clearStepEntriesFromUid(int)}) so {@code StepSequenceExecutor}'s
   * resume-skip guard re-executes the body instead of mistaking the previous iteration's outputs
   * for this iteration's own. Without this clear, every loop strategy ran its body on iteration 1
   * only while still emitting {@code LOOP_ITERATION_STARTED}/{@code LOOP_ITERATION_COMPLETED} for
   * each iteration.
   *
   * <p>Context values and generated-artifact descriptors written by the previous iteration are
   * deliberately preserved: advancing a loop is not a rewind — earlier iterations really ran, so
   * their context writes stay visible to later iterations (until overwritten) and their emitted
   * artifacts stay recorded. That preserved handoff is what lets a rework/refinement loop read the
   * previous iteration's result.
   *
   * <p>The previous iteration's body range is identified by the uid at which it began
   * ({@link WorkflowState#getLoopIterationBodyStartUid(String)}), recorded the last time this
   * method genuinely started a new iteration. A resume into a paused iteration is detected by the
   * persisted cursor already equalling {@code iteration}: in that case nothing is cleared, so
   * steps completed before the pause are still skipped correctly on re-entry.
   *
   * @return {@code true} when {@code iteration} is genuinely being entered for the first time
   *     (the persisted cursor did not already equal it) — {@code false} when this call is instead
   *     resuming an iteration already in progress (a pause is being redriven). Callers use this to
   *     gate {@code LOOP_ITERATION_STARTED} so a resumed iteration is not double-counted.
   */
  protected static boolean markLoopIterationStart(ExecutionContext executionContext,
      String blueprintId, int iteration) {
    WorkflowState state = executionContext.getState();
    boolean newIterationEntry = state.getLoopIterationCursor(blueprintId) != iteration;
    if (newIterationEntry) {
      int previousBodyStartUid = state.getLoopIterationBodyStartUid(blueprintId);
      if (previousBodyStartUid > 0) {
        state.clearStepEntriesFromUid(previousBodyStartUid);
      }
      state.setLoopIterationBodyStartUid(blueprintId, executionContext.peekNextStepSequenceUid());
    }
    state.setLoopIterationCursor(blueprintId, iteration);
    return newIterationEntry;
  }

  protected static void clearLoopIterationCursor(WorkflowState state, String blueprintId) {
    state.clearLoopIterationCursor(blueprintId);
  }

  /**
   * Execute a single iteration of the blueprint body, emitting start and complete loop events —
   * unless nothing in the body actually executed.
   *
   * <p>A self-terminating loop ({@code FIXED_COUNT}/{@code FOR_EACH}) that already ran to
   * completion is still re-entered on every later top-level workflow redrive (it is never marked
   * complete the way signal-terminated loops are, since {@code FOR_EACH} must keep re-checking its
   * list for mutation). On such a redrive every body step is already in {@code stepOutputs} from the
   * original pass, so {@code StepSequenceExecutor}'s resume-skip guard skips the whole body — no
   * step, nested or otherwise, allocates a new execution uid. Emitting the iteration events in that
   * case would claim an iteration happened when nothing did, the same audit-integrity defect this
   * class exists to prevent for the body itself. Comparing {@link ExecutionContext#peekNextStepSequenceUid()}
   * before and after detects this generically (it only advances when a real step executes,
   * regardless of nesting), so the events are deferred until after the body runs and only recorded
   * when at least one step genuinely executed — including a step that started and then paused, which
   * still allocates its uid before pausing.
   *
   * <p>{@code LOOP_ITERATION_STARTED} is additionally gated on {@code newIterationEntry} (see
   * {@link #markLoopIterationStart}): a redrive that resumes an iteration already in progress (the
   * one that just paused) must not record a second {@code STARTED} for the same iteration index.
   * {@code LOOP_ITERATION_COMPLETED} is gated on the iteration's outcome genuinely being
   * {@link ExecutionOutcome#COMPLETED} — a {@code PAUSED} or {@code FAILED} outcome must never record
   * a completion for an iteration that did not complete; when a resumed iteration goes on to finish
   * cleanly, {@code COMPLETED} is recorded then (on this later call), not before. This holds even
   * when the resumed call itself allocates no new uid at all — a body whose only remaining step was
   * already uid-allocated before a pause (for example a lone {@code INPUT} step) resumes, answers,
   * and completes without the resume-skip guard allocating anything fresh; {@code COMPLETED} is still
   * recorded for that already-started iteration (see the uid-unchanged branch below), while a
   * brand-new iteration entry whose body allocates nothing at all (a self-terminating loop already
   * fully done, redriven with everything skipped) still emits neither event.
   *
   * <p>An uncaught {@link RuntimeException} from the body is handled the same way: {@code
   * StepSequenceExecutor} allocates a step's execution uid before invoking its behaviour, so the uid
   * counter has already advanced by the time such a step throws. The counter is therefore still
   * checked (in a {@code catch}, before the exception propagates) so a genuine mid-body failure still
   * leaves a {@code LOOP_ITERATION_STARTED} audit entry when this is a new iteration entry — only
   * {@code COMPLETED} is inherently unreachable on this path, since the iteration never actually
   * completed.
   *
   * <p>Brackets the body call with {@link ExecutionContext#pushActiveLoopBlueprint(String)}/
   * {@link ExecutionContext#popActiveLoopBlueprint()} so a rewind issued from inside the body — for
   * example {@code RetryPreviousBehaviourHandler} retrying this iteration's own first-executed step —
   * can tell {@code WorkflowState.clearEntriesFromUid} that this loop's iteration is still genuinely in
   * progress, not being externally re-entered, so its cursor/body-start-uid bookkeeping must survive
   * the rewind.
   *
   * @param newIterationEntry whether {@code iteration} is genuinely being entered for the first
   *                          time on this call (from {@link #markLoopIterationStart}), as opposed to
   *                          resuming an iteration already in progress
   */
  protected ExecutionOutcome executeIteration(BlueprintDefinition blueprint,
      int iteration,
      ExecutionContext executionContext,
      boolean newIterationEntry) {
    if (executionContext.getState().getStatus() == WorkflowStatus.CANCELLED) {
      return ExecutionOutcome.PAUSED;
    }
    int uidBeforeIteration = executionContext.peekNextStepSequenceUid();
    String runId = executionContext.getState().getRunId();
    String payload = "iteration=%d".formatted(iteration);
    executionContext.pushActiveLoopBlueprint(blueprint.blueprintId());
    ExecutionOutcome outcome;
    try {
      outcome = stepSequenceExecutor.executeAll(blueprint.steps(), executionContext);
    } catch (RuntimeException exception) {
      if (newIterationEntry && executionContext.peekNextStepSequenceUid() != uidBeforeIteration) {
        eventRecorder.record(runId, blueprint.blueprintId(),
            WorkflowEventType.LOOP_ITERATION_STARTED, payload, "runtime");
      }
      throw exception;
    } finally {
      executionContext.popActiveLoopBlueprint();
    }
    if (executionContext.peekNextStepSequenceUid() == uidBeforeIteration) {
      // No new uid was allocated on this call. Usually that means nothing genuinely happened (a
      // self-terminating loop already fully done, redriven with its whole body skipped) — but when
      // this is a RESUME of an iteration already marked started on an earlier call
      // (newIterationEntry == false) and the body outcome is genuinely COMPLETED, the iteration did
      // just complete: its only remaining step (for example a paused INPUT) already carried a
      // recorded uid/output from before the pause, so resuming it answers the step without the
      // resume-skip guard allocating a fresh one. STARTED was already recorded on that earlier
      // call, so only COMPLETED is missing here.
      if (!newIterationEntry && outcome == ExecutionOutcome.COMPLETED) {
        eventRecorder.record(runId, blueprint.blueprintId(),
            WorkflowEventType.LOOP_ITERATION_COMPLETED, payload, "runtime");
      }
      return outcome;
    }
    if (newIterationEntry) {
      eventRecorder.record(runId, blueprint.blueprintId(),
          WorkflowEventType.LOOP_ITERATION_STARTED, payload, "runtime");
    }
    if (outcome == ExecutionOutcome.COMPLETED) {
      eventRecorder.record(runId, blueprint.blueprintId(),
          WorkflowEventType.LOOP_ITERATION_COMPLETED, payload, "runtime");
    }
    return outcome;
  }

  /**
   * Runs a single bounded iteration of the body: advances the loop cursor, logs, and delegates to
   * {@link #executeIteration}. Shared by the strategies so the per-iteration bookkeeping lives in
   * one place.
   */
  protected ExecutionOutcome runIteration(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, int iteration) {
    boolean newIterationEntry =
        markLoopIterationStart(executionContext, blueprint.blueprintId(), iteration);
    LOG.log(System.Logger.Level.DEBUG,
        "Loop iteration start strategy={0}, iteration={1}, maxIterations={2}",
        strategy(), iteration, config.maxIterations());
    ExecutionOutcome outcome =
        executeIteration(blueprint, iteration, executionContext, newIterationEntry);
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
