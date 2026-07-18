// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryMode;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.SparBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.retry.RetryPolicy;
import com.agentforge4j.runtime.GeneratedArtifactStore;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import com.agentforge4j.runtime.execution.GeneratedArtifactEviction;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.RetryPolicyAttemptCounter;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;
import java.util.List;
import org.apache.commons.lang3.math.NumberUtils;

public final class RetryPreviousBehaviourHandler implements
    BehaviourHandler<RetryPreviousBehaviour> {

  private static final System.Logger LOG = System.getLogger(
      RetryPreviousBehaviourHandler.class.getName());

  /**
   * Reserved-key layout: every runtime-owned retry key is a fixed prefix with the step id
   * <em>last</em>, and no fixed prefix (here or in {@link RetryPolicyAttemptCounter}) is a prefix
   * of another — so keys from different families can never be equal, whatever the step ids are.
   * The previous {@code __retry_<id>_attempts} shape put the id in the middle, letting a step
   * literally named {@code policy_x} alias the shared {@code __retry_policy_x_attempts} counter of
   * a step named {@code x} — silent cross-talk between two unrelated budgets.
   */
  private static final String RETRY_COUNTER_PREFIX = "__retry_previous_attempts:";

  /**
   * Reserved, {@code __}-prefixed context key (per owning {@code RETRY_PREVIOUS} step id) recording
   * that a replay dispatch of that step is currently in flight — written when a dispatch pauses
   * inside its own replay range, cleared when the dispatch genuinely completes. Lets the re-entry
   * drive <em>resume</em> the interrupted dispatch (skipping range steps that already completed,
   * reserving no further local or shared attempt, and never re-clearing state the pause's
   * resolution just satisfied) instead of restarting it from scratch. Being {@code __}-prefixed it
   * survives {@link WorkflowState#clearEntriesFromUid}'s sweep, so re-entry pairs it with the
   * replayed target's execution uid as a staleness anchor: an external rewind that wiped the
   * target's uid invalidates the marker.
   */
  private static final String INFLIGHT_MARKER_PREFIX = "__retry_previous_inflight:";

  /**
   * Synthetic, non-agent step-output marker recorded for the owning {@code RETRY_PREVIOUS} step
   * itself once its dispatch genuinely completes (mirrors the pattern
   * {@code DefaultWorkflowRuntime.advancePastToolStep} uses for its own synthetic marker). Lets
   * {@code StepSequenceExecutor.shouldSkip} treat the step as done on a later resume re-drive, so a
   * downstream pause/resume no longer re-fires this step, burns another attempt, and wipes state the
   * resume just satisfied. The value carries no meaning beyond "recorded" — never surfaced to a
   * user.
   */
  private static final String RETRY_COMPLETION_MARKER = "retry-previous:dispatched";

  private final EventRecorder eventRecorder;
  private final GeneratedArtifactStore generatedArtifactStore;
  private ExecutableExecutor executableExecutor;

  public RetryPreviousBehaviourHandler(EventRecorder eventRecorder,
      GeneratedArtifactStore generatedArtifactStore) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.generatedArtifactStore =
        Validate.notNull(generatedArtifactStore, "generatedArtifactStore must not be null");
  }

  public void setExecutableExecutor(ExecutableExecutor executableExecutor) {
    this.executableExecutor = Validate.notNull(executableExecutor,
        "executableExecutor must not be null");
  }

  @Override
  public Class<RetryPreviousBehaviour> behaviourType() {
    return RetryPreviousBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step,
      RetryPreviousBehaviour behaviour,
      ExecutionContext executionContext) {
    Validate.notNull(executableExecutor,
        "executableExecutor must be configured on RetryPreviousBehaviourHandler");
    WorkflowState state = executionContext.getState();
    String attemptKey = RETRY_COUNTER_PREFIX + behaviour.retryStepId();
    int attempts = readAttemptCount(state, attemptKey);

    // Structural pre-mutation validation phase: runs unconditionally, before the local maxAttempts
    // check, before either counter is incremented, and before clearEntriesFromUid — a misconfigured
    // RETRY_PREVIOUS (a composite executable whose cleared state this handler never replays, or a
    // target whose RetryPolicy forbids rewind-retry outright) must fail loudly on every invocation,
    // leaving state untouched, rather than silently corrupting state or bypassing governance on
    // whichever attempt happens to run. This deliberately excludes the target's shared RetryPolicy
    // ceiling: that budget governs only a genuine replay dispatch (below), never the workflow-authored
    // fallback, so it must not be checked — let alone reject — before the fallback-vs-replay decision.
    RetryPolicy targetPolicy = resolveTargetRetryPolicy(behaviour, executionContext);
    validateAllowRetryFromPrevious(step, behaviour, targetPolicy);
    validateReplayRangeContainsOnlyPlainSteps(step, behaviour,
        executionContext.getCurrentSequenceExecutableList());

    String inflightKey = INFLIGHT_MARKER_PREFIX + step.stepId();
    if (isResumingInterruptedDispatch(state, behaviour, inflightKey)) {
      // A dispatch of this step already reserved its budgets, cleared the range, and then paused
      // inside its own replay: continue that same dispatch — skip range steps that already
      // completed, consume no further local attempt and no further shared-ceiling reservation,
      // and leave the state the pause's resolution just satisfied untouched. Restarting instead
      // would double-burn both budgets per logical retry (failing the run outright when the
      // shared ceiling exhausts first, where the authored fallback was intended) and re-clear the
      // range, wiping a just-submitted input and re-prompting. No further STEP_RETRIED event is
      // recorded: the interrupted attempt already recorded its own, and this is that same attempt.
      LOG.log(System.Logger.Level.INFO,
          "Resuming interrupted retry dispatch stepId={0}, retryStepId={1} (no further attempt consumed)",
          step.stepId(), behaviour.retryStepId());
      ExecutionOutcome resumedOutcome = switch (behaviour.retryMode()) {
        case SINGLE_STEP -> resumeSingleStep(step, behaviour, executionContext);
        case FROM_STEP -> executeFromStep(step, behaviour, executionContext, true);
      };
      return markCompletionIfDispatched(step, resumedOutcome, executionContext);
    }
    // Any marker that survived to here is stale (its dispatch anchor — the target's execution uid
    // — was wiped by an external rewind): drop it so this entry dispatches fresh.
    state.removeContextValue(inflightKey);

    LOG.log(System.Logger.Level.INFO,
        "Retry behaviour start stepId={0}, retryStepId={1}, attempt={2}, maxAttempts={3}",
        step.stepId(), behaviour.retryStepId(), attempts + 1, behaviour.maxAttempts());
    if (attempts >= behaviour.maxAttempts()) {
      // The local RETRY_PREVIOUS cap is exhausted: run the workflow-authored fallback instead,
      // regardless of the target's shared RetryPolicy ceiling — reached, exhausted, or not — since
      // this never re-executes retryStepId and so never touches that budget.
      LOG.log(System.Logger.Level.WARNING, "Retry max attempts reached stepId={0}, retryStepId={1}",
          step.stepId(), behaviour.retryStepId());
      eventRecorder.record(state.getRunId(), step.stepId(),
          WorkflowEventType.STEP_RETRIED,
          "maxAttempts %d reached for retryStepId '%s', executing fallback"
              .formatted(behaviour.maxAttempts(), behaviour.retryStepId()),
          "runtime");
      ExecutionOutcome fallbackOutcome = executeStep(behaviour.fallback(), executionContext);
      return markCompletionIfDispatched(step, fallbackOutcome, executionContext);
    }

    Integer retryUid = state.getStepExecutionUid().get(behaviour.retryStepId());
    Validate.notNull(retryUid, () -> new StepExecutionException(
        "RetryPreviousBehaviour in step '%s' references step '%s' which has not been executed yet"
            .formatted(step.stepId(), behaviour.retryStepId())));

    // Reserved atomically (check-and-increment as one indivisible operation — see
    // RetryPolicyAttemptCounter.reserve) only now that every rejecting precondition, including the
    // retryUid check just above, has passed: nothing left below this point can reject the dispatch,
    // so a granted reservation is never left stranded against a replay that did not happen.
    if (targetPolicy != null) {
      reserveSharedRetryPolicyCeiling(step, behaviour, state, targetPolicy);
    }

    attempts++;
    state.putContextValue(attemptKey,
        new StringContextValue(String.valueOf(attempts), ContextProvenance.SYSTEM_GENERATED));

    // Evict captured bytes for artifacts emitted at or after the rewind point before clearing the
    // descriptors, so a re-emit on the re-drive upserts cleanly and an un-re-emitted path does not linger.
    GeneratedArtifactEviction.evictFromUid(generatedArtifactStore, state, retryUid);
    // Excludes any loop whose iteration is currently active on the call stack (this retry may be
    // running from inside one, retrying that same iteration's own first-executed step, whose uid can
    // coincide exactly with the loop's recorded body-start-uid) — that loop is not being externally
    // re-entered, so its cursor/body-start-uid bookkeeping must survive this rewind.
    state.clearEntriesFromUid(retryUid, executionContext.activeLoopBlueprintIds());
    LOG.log(System.Logger.Level.DEBUG, "Retry clearFromUid retryUid={0}", retryUid);
    LOG.log(System.Logger.Level.DEBUG, "Retry dispatched retryMode={0}, retryStepId={1}",
        behaviour.retryMode(), behaviour.retryStepId());
    ExecutionOutcome outcome = switch (behaviour.retryMode()) {
      case SINGLE_STEP -> executeSingleStep(step, behaviour, executionContext);
      case FROM_STEP -> executeFromStep(step, behaviour, executionContext, false);
    };
    if (outcome == ExecutionOutcome.PAUSED) {
      // The dispatch paused inside its own replay: record the in-flight marker so the re-entry
      // drive resumes this dispatch instead of restarting it (see INFLIGHT_MARKER_PREFIX).
      state.putContextValue(inflightKey,
          new StringContextValue(String.valueOf(attempts), ContextProvenance.SYSTEM_GENERATED));
    }

    eventRecorder.record(state.getRunId(), step.stepId(),
        WorkflowEventType.STEP_RETRIED,
        "attempt %d of %d, retryMode=%s, retryStepId='%s'"
            .formatted(attempts, behaviour.maxAttempts(),
                behaviour.retryMode(), behaviour.retryStepId()),
        "runtime");

    return markCompletionIfDispatched(step, outcome, executionContext);
  }

  /**
   * Records the completion marker for the owning {@code RETRY_PREVIOUS} step once its dispatch
   * (replay or fallback) genuinely completes ({@code outcome == COMPLETED}), allocating a fresh
   * uid for it strictly after the dispatch above — never the stale uid this step held before its
   * retry target was rewound and replayed. A replayed target always allocates its own fresh uid
   * before this point, so the marker's uid is always higher than everything genuinely replayed
   * during this dispatch: a later rewind whose threshold reaches the replayed target (uid at or
   * below the target's fresh uid) is therefore guaranteed to reach this marker too, keeping the two
   * in the same uid-ordered sweep {@link WorkflowState#clearEntriesFromUid}/{@link
   * WorkflowState#clearStepEntriesFromUid} performs for every other step's output — no parallel
   * bookkeeping, and no marker left stranded at a uid earlier than state it was recorded after. A
   * non-{@code COMPLETED} outcome (a pause within this step's own replay) leaves no completion
   * marker; the separate in-flight marker {@code handle} records for a paused replay makes the next
   * drive re-enter this step and <em>resume</em> the interrupted dispatch where it paused —
   * skipping range steps that already completed, reserving no further local or shared attempt —
   * never restarting it. A completed dispatch also clears that in-flight marker here.
   */
  private static ExecutionOutcome markCompletionIfDispatched(StepDefinition step,
      ExecutionOutcome outcome, ExecutionContext executionContext) {
    if (outcome == ExecutionOutcome.COMPLETED) {
      WorkflowState state = executionContext.getState();
      int freshUid = executionContext.allocateStepSequenceUid();
      state.putStepExecutionUid(step.stepId(), freshUid);
      state.putStepOutput(step.stepId(), RETRY_COMPLETION_MARKER);
      state.removeContextValue(INFLIGHT_MARKER_PREFIX + step.stepId());
    }
    return outcome;
  }

  /**
   * An interrupted dispatch of this owning step is resumable when its in-flight marker is present
   * <em>and</em> the replayed target still has an execution uid — the dispatch's anchor. A marker
   * whose anchor was wiped (an external rewind, e.g. {@code WorkflowRuntime.retry}, cleared the
   * range including the target's uid) no longer describes anything resumable and is treated as
   * stale: the caller drops it and dispatches fresh, reserving budgets properly.
   */
  private static boolean isResumingInterruptedDispatch(WorkflowState state,
      RetryPreviousBehaviour behaviour, String inflightKey) {
    if (state.getContext().get(inflightKey) == null) {
      return false;
    }
    return state.getStepExecutionUid().get(behaviour.retryStepId()) != null;
  }

  /**
   * {@code SINGLE_STEP} arm of an in-flight dispatch resumption: the interrupted dispatch's only
   * replayed step is the target itself, so if the pause's resolution already completed it (it
   * bears a step output) there is nothing left to execute; otherwise the target is executed as the
   * continuation of the same dispatch.
   */
  private ExecutionOutcome resumeSingleStep(StepDefinition step, RetryPreviousBehaviour behaviour,
      ExecutionContext executionContext) {
    if (executionContext.getState().getStepOutputs().containsKey(behaviour.retryStepId())) {
      return ExecutionOutcome.COMPLETED;
    }
    return executeSingleStep(step, behaviour, executionContext);
  }

  /**
   * Atomically reserves one attempt against {@code targetPolicy}'s shared
   * {@link RetryPolicyAttemptCounter} budget — across both {@code WorkflowRuntime.retry()} and every
   * {@code RETRY_PREVIOUS} step targeting the same step — rejecting if {@code maxAttempts} is already
   * reached. {@code RetryPolicy.maxAttempts} is the hard aggregate ceiling for retries of that step
   * across every supported mechanism; {@code RetryPreviousBehaviour.maxAttempts} (checked separately,
   * and enforced by falling back rather than rejecting) is an additional local cap, never a way to
   * exceed this one. The check and the increment are one atomic operation (see
   * {@link RetryPolicyAttemptCounter#reserve}), not a separate read followed by a separate increment,
   * so two concurrent callers racing at the ceiling can never both be granted.
   */
  private static void reserveSharedRetryPolicyCeiling(StepDefinition step,
      RetryPreviousBehaviour behaviour, WorkflowState state, RetryPolicy targetPolicy) {
    boolean reserved =
        RetryPolicyAttemptCounter.reserve(state, behaviour.retryStepId(), targetPolicy.maxAttempts());
    Validate.isTrue(reserved, () -> new StepExecutionException(
        ("RetryPreviousBehaviour step '%s': target step '%s' RetryPolicy maxAttempts (%d) already "
            + "reached (%d attempts already used across retry() and RETRY_PREVIOUS); rejected")
            .formatted(step.stepId(), behaviour.retryStepId(), targetPolicy.maxAttempts(),
                RetryPolicyAttemptCounter.read(state, behaviour.retryStepId()))));
  }

  /**
   * Resolves {@code behaviour.retryStepId()}'s {@link RetryPolicy}, when it names a plain
   * {@link StepDefinition} carrying one (an {@link AgentBehaviour} or {@link SparBehaviour}).
   * Returns {@code null} for a target with no {@code RetryPolicy} concept (any other step type), or
   * one this handler cannot yet resolve (surfaced instead by the mode-specific structural validation
   * later) — callers must treat a {@code null} result as "unrestricted by target policy", never
   * invent a new restriction for step types that never had this concept.
   */
  private static RetryPolicy resolveTargetRetryPolicy(RetryPreviousBehaviour behaviour,
      ExecutionContext executionContext) {
    Executable target = executionContext.getCurrentSequenceExecutables().get(behaviour.retryStepId());
    if (!(target instanceof StepDefinition targetStep)) {
      return null;
    }
    return retryPolicyOf(targetStep.behaviour());
  }

  /**
   * Rejects the retry unless {@code targetPolicy}'s {@code allowRetryFromPrevious} is {@code true}.
   * A {@code null} {@code targetPolicy} (no {@code RetryPolicy} concept, or an unresolvable target)
   * is treated as allowed.
   */
  private void validateAllowRetryFromPrevious(StepDefinition step, RetryPreviousBehaviour behaviour,
      RetryPolicy targetPolicy) {
    if (targetPolicy == null) {
      return;
    }
    Validate.isTrue(targetPolicy.allowRetryFromPrevious(), () -> new StepExecutionException(
        ("RetryPreviousBehaviour step '%s': target step '%s' has allowRetryFromPrevious=false in its "
            + "RetryPolicy; RETRY_PREVIOUS into it is rejected")
            .formatted(step.stepId(), behaviour.retryStepId())));
  }

  private static RetryPolicy retryPolicyOf(StepBehaviour behaviour) {
    if (behaviour instanceof AgentBehaviour agentBehaviour) {
      return agentBehaviour.retryPolicy();
    }
    if (behaviour instanceof SparBehaviour sparBehaviour) {
      return sparBehaviour.retryPolicy();
    }
    return null;
  }

  /**
   * Rejects a replay range that would let something read state that a composite
   * ({@link BlueprintRef} or nested {@link WorkflowDefinition}) inside the range wrote, after
   * {@code clearEntriesFromUid} wiped that composite's state without this handler ever replaying it
   * — silent partial-state corruption. Uses the full ordered executable list (unlike
   * {@code getCurrentSequenceStepIds()}/{@code getCurrentSequenceExecutables()}, which only ever hold
   * plain steps) so a composite sitting between the boundaries is visible. Structural problems
   * (retryStepId or the owning step missing from the sequence, or out of order) are left to the
   * mode-specific validation later in {@code executeSingleStep}/{@code executeFromStep} — this method
   * silently returns rather than duplicating those checks with a different message.
   *
   * <p>Every composite in the checked span is rejected unconditionally, for both retry modes, with
   * no "trailing composite is safe" exception: a composite with nothing plain after it within the
   * handler's own inline replay can still be read stale by whatever the enclosing top-level sequence
   * runs immediately after the owning {@code RETRY_PREVIOUS} step, in this very drive, if that
   * continuation does not itself pause — for example {@code [A, blueprint(writes K), R(FROM_STEP A),
   * V(reads K)]}, where {@code R}'s inline replay only ever re-executes the plain step {@code A} (the
   * blueprint is invisible to it), {@code R} then completes, and {@code V} runs immediately after in
   * the same drive with {@code K} already cleared and never regenerated. Whether the top-level
   * sequence happens to pause before reaching such a continuation is not something this validation
   * can rely on, so it rejects the shape unconditionally rather than trying to prove it safe.
   *
   * <p>For {@code FROM_STEP} the checked span is {@code [retryStepId, owningStep)} — {@code
   * retryStepId} itself is always a plain step (a composite cannot be a retry target), so starting
   * the span there has no effect beyond making the boundary explicit. For {@code SINGLE_STEP} only
   * {@code retryStepId} itself is ever inline-replayed, so the checked span is the strictly-between
   * {@code (retryStepId, owningStep)}.
   */
  private void validateReplayRangeContainsOnlyPlainSteps(StepDefinition step,
      RetryPreviousBehaviour behaviour, List<Executable> fullSequence) {
    int retryIndex = indexOfStepId(fullSequence, behaviour.retryStepId());
    int owningIndex = indexOfStepId(fullSequence, step.stepId());
    if (retryIndex < 0 || owningIndex < 0 || retryIndex >= owningIndex) {
      return;
    }
    int rangeStart = behaviour.retryMode() == RetryMode.FROM_STEP ? retryIndex : retryIndex + 1;
    for (int i = rangeStart; i < owningIndex; i++) {
      Executable candidate = fullSequence.get(i);
      if (!(candidate instanceof StepDefinition)) {
        throw compositeRejection(step, behaviour, candidate);
      }
    }
  }

  private static StepExecutionException compositeRejection(StepDefinition step,
      RetryPreviousBehaviour behaviour, Executable composite) {
    return new StepExecutionException(
        ("RetryPreviousBehaviour step '%s': replay range for retryStepId '%s' contains a "
            + "non-step executable '%s' whose cleared state would be read stale; composite "
            + "(BlueprintRef / nested WorkflowDefinition) replay in this position is not supported")
            .formatted(step.stepId(), behaviour.retryStepId(), describeExecutable(composite)));
  }

  private static int indexOfStepId(List<Executable> executables, String stepId) {
    for (int i = 0; i < executables.size(); i++) {
      if (executables.get(i) instanceof StepDefinition stepDefinition
          && stepDefinition.stepId().equals(stepId)) {
        return i;
      }
    }
    return -1;
  }

  private static String describeExecutable(Executable executable) {
    if (executable instanceof BlueprintRef ref) {
      return "BlueprintRef:" + ref.blueprintId();
    }
    if (executable instanceof WorkflowDefinition workflowDefinition) {
      return "WorkflowDefinition:" + workflowDefinition.id();
    }
    return executable.getClass().getSimpleName();
  }

  private ExecutionOutcome executeSingleStep(StepDefinition step,
      RetryPreviousBehaviour behaviour,
      ExecutionContext executionContext) {
    List<String> orderedIds = executionContext.getCurrentSequenceStepIds();
    Executable target = resolveExecutable(behaviour.retryStepId(), orderedIds, executionContext,
        step.stepId());
    return executeStep(target, executionContext);
  }

  /**
   * Replays the {@code [retryStepId, owningStep)} range in order. With
   * {@code skipAlreadyCompleted} (an in-flight dispatch resumption) a range step that already
   * bears a step output — recorded either by the interrupted dispatch itself or by the pause's
   * resolution (e.g. {@code submitInput}) — is not re-executed: the range was cleared once, at the
   * interrupted dispatch's start, so any output present now belongs to this same dispatch. A fresh
   * dispatch passes {@code false}; its range was just cleared, so nothing could be skipped anyway.
   */
  private ExecutionOutcome executeFromStep(StepDefinition step,
      RetryPreviousBehaviour behaviour,
      ExecutionContext executionContext,
      boolean skipAlreadyCompleted) {
    List<String> orderedIds = executionContext.getCurrentSequenceStepIds();
    int fromIndex = orderedIds.indexOf(behaviour.retryStepId());
    int toIndex = orderedIds.indexOf(step.stepId());

    Validate.isGreaterThan(fromIndex, -1, () -> new StepExecutionException(
        "RetryPreviousBehaviour step '%s': retryStepId '%s' not found in current sequence"
            .formatted(step.stepId(), behaviour.retryStepId())));
    Validate.isGreaterThan(toIndex, -1, () -> new StepExecutionException(
        "RetryPreviousBehaviour step '%s': owning step not found in current sequence"
            .formatted(step.stepId())));
    Validate.isTrue(fromIndex < toIndex, () -> new StepExecutionException(
        "RetryPreviousBehaviour step '%s': retryStepId '%s' must appear before owning step in current sequence"
            .formatted(step.stepId(), behaviour.retryStepId())));

    WorkflowState state = executionContext.getState();
    ExecutionOutcome last = ExecutionOutcome.COMPLETED;
    for (String rangeStepId : orderedIds.subList(fromIndex, toIndex)) {
      if (skipAlreadyCompleted && state.getStepOutputs().containsKey(rangeStepId)) {
        continue;
      }
      Executable target = resolveExecutable(rangeStepId, orderedIds, executionContext,
          step.stepId());
      last = executeStep(target, executionContext);
      if (last != ExecutionOutcome.COMPLETED) {
        return last;
      }
    }
    return last;
  }

  /**
   * Executes a retry target (single-step, from-step range, or fallback), allocating a fresh
   * step-execution uid for a {@link StepDefinition} first. Every retry target runs outside
   * {@code StepSequenceExecutor}, which is where a step's uid is normally allocated — the retry
   * uid range has just been cleared via {@code clearEntriesFromUid}, so without this an AGENT (or
   * any uid-dependent) target would see a {@code null} current-step uid. Mirrors the uid allocation
   * {@code BranchBehaviourHandler} performs for a directly executed branch step.
   */
  private ExecutionOutcome executeStep(Executable executable, ExecutionContext executionContext) {
    if (executable instanceof StepDefinition stepDefinition) {
      executionContext.getState().putStepExecutionUid(stepDefinition.stepId(),
          executionContext.allocateStepSequenceUid());
    }
    return executableExecutor.execute(executable, executionContext);
  }

  private Executable resolveExecutable(String stepId,
      List<String> orderedIds,
      ExecutionContext executionContext,
      String owningStepId) {
    Validate.notNull(orderedIds, "orderedIds must not be null");
    Executable target = executionContext.getCurrentSequenceExecutables().get(stepId);
    Validate.notNull(target, () -> new StepExecutionException(
        "RetryPreviousBehaviour step '%s': cannot resolve Executable for stepId '%s'"
            .formatted(owningStepId, stepId)));
    return target;
  }

  private static int readAttemptCount(WorkflowState state, String attemptKey) {
    ContextValue existing = state.getContext().get(attemptKey);
    if (existing == null) {
      return 0;
    }
    if (existing instanceof StringContextValue stringContextValue) {
      return NumberUtils.toInt(stringContextValue.value(), 0);
    }
    return 0;
  }
}
