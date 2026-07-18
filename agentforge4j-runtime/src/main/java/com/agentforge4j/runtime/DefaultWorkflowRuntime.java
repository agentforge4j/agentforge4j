// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.validation.WorkflowValidator;
import com.agentforge4j.core.exception.ExecutionNotFoundException;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.runtime.StepApprovalDecision.Approve;
import com.agentforge4j.core.runtime.StepApprovalDecision.Reject;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.tool.ToolDecision;
import com.agentforge4j.core.spi.tool.ToolExecutionOutcome;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
import com.agentforge4j.core.spi.tool.ToolInvocationClaimLostException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowCapturePathCollector;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowTreeWalker;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.SparBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.retry.RetryPolicy;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.GeneratedArtifactEviction;
import com.agentforge4j.runtime.execution.RequirementCheckpoint;
import com.agentforge4j.runtime.execution.RetryPolicyAttemptCounter;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.execution.TransitionGate;
import com.agentforge4j.runtime.interceptor.ExecutionBlockedException;
import com.agentforge4j.runtime.interceptor.RunExecutionContext;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.tool.ToolResultApplier;
import com.agentforge4j.util.Validate;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

/**
 * Default {@link WorkflowRuntime} implementation.
 *
 * <p>Owns the outer drive loop: resolves the workflow, creates or retrieves a
 * {@link WorkflowState}, delegates body execution to {@link StepSequenceExecutor}, reacts to pauses and completions by
 * updating the state and emitting events, and persists state through the injected repository after each drive.
 *
 * <p>Not thread-safe per run — a single run is driven by the caller's thread at
 * any given time. The embedding application is responsible for preventing concurrent drives of the same run.
 *
 * <p>Construction is package-private and goes through {@link WorkflowRuntimeBuilder}. The
 * constructors take internal collaborators ({@link StepSequenceExecutor}, {@link ExecutableExecutor}) from the
 * non-exported {@code com.agentforge4j.runtime.execution} package, so they must not be part of the exported public
 * API.
 */
public final class DefaultWorkflowRuntime implements WorkflowRuntime {

  private static final System.Logger LOG = System.getLogger(DefaultWorkflowRuntime.class.getName());

  /**
   * Default maximum workflow nesting depth — guards against pathological configs.
   */
  public static final int DEFAULT_MAX_NESTING_DEPTH = 32;

  private final WorkflowRepository workflowRepository;
  private final WorkflowStateRepository workflowStateRepository;
  private final StepSequenceExecutor stepSequenceExecutor;
  private final EventRecorder eventRecorder;
  private final Clock clock;
  private final RunContextManager runContextManager;
  private final int maxNestingDepth;
  private final StepTreeSearcher stepTreeSearcher;
  private final FailureSanitiser failureSanitiser;
  private final ToolExecutionService toolExecutionService;
  private final PendingToolInvocationStore pendingToolInvocationStore;
  private final ToolResultApplier toolResultApplier;
  private final RequirementResolver requirementResolver;
  private final TransitionGate transitionGate;
  private final RunExecutionInterceptor runExecutionInterceptor;
  private final GeneratedArtifactStore generatedArtifactStore;

  /**
   * Fixed-size pool of monitor objects guarding the terminal-status transition ({@code cancel()}
   * racing a concurrent drive's own completion/failure decision, and a concurrent completion racing
   * a concurrent failure) against another thread mutating the same in-process {@link WorkflowState}
   * instance — {@link WorkflowStateRepository} implementations "may return live mutable
   * {@code WorkflowState} instances", and this runtime's own in-memory repository always does, so
   * two callers acting on the same run id can genuinely hold the same object. Synchronizing the
   * check-then-set sequence in {@link #cancel}, {@link #finaliseDrive}, {@link #failRun},
   * {@link #handleRejected}, {@link #enforceCancellationWon}, and
   * {@link #persistSuspensionCancellationWins} on the same per-run stripe makes exactly one of a
   * racing cancel/complete/fail set win the terminal status, with every loser's guard (already
   * checking the terminal status before acting) correctly observing the winner's fully-applied
   * change.
   *
   * <p>A run id is mapped to one of a fixed {@link #RUN_LOCK_STRIPE_COUNT} stripes by hash, striped
   * locking (the same technique {@code java.util.concurrent.locks.StripedLock}-style utilities use)
   * rather than one monitor allocated per run id forever: memory is bounded at
   * {@link #RUN_LOCK_STRIPE_COUNT} monitors for the lifetime of this runtime instance, regardless of
   * how many distinct runs it ever drives, unlike a map that grows by one entry per run id and never
   * shrinks. Two unrelated run ids occasionally hashing to the same stripe only costs a little extra
   * contention between those two runs' terminal transitions, never a correctness problem.
   *
   * <p>This closes only the in-process race. A durable or distributed {@link WorkflowStateRepository}
   * serving multiple runtime processes must provide its own cross-process concurrency control (for
   * example optimistic locking on save, or a transactional read-modify-write) — no in-process monitor
   * can substitute for that, and this pool is never a source of truth across processes.
   */
  private static final int RUN_LOCK_STRIPE_COUNT = 256;

  private final Object[] runLockStripes = createRunLockStripes();

  private static Object[] createRunLockStripes() {
    Object[] stripes = new Object[RUN_LOCK_STRIPE_COUNT];
    for (int i = 0; i < stripes.length; i++) {
      stripes[i] = new Object();
    }
    return stripes;
  }

  private Object runLock(String runId) {
    int index = Math.floorMod(runId.hashCode(), runLockStripes.length);
    return runLockStripes[index];
  }

  /**
   * Re-validates the live workflow registry at {@code start()}, immediately before any run state or
   * {@code RUN_STARTED} event is created — {@code WorkflowRuntimeBuilder.build()}'s own check only
   * ever saw a snapshot taken at construction time, and a dynamic or hot-reloadable
   * {@link WorkflowRepository} can return a different (or newly broken) definition by the time a run
   * actually starts.
   */
  private static final WorkflowValidator START_TIME_VALIDATOR =
      new WorkflowValidator(WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH);

  DefaultWorkflowRuntime(WorkflowRepository workflowRepository,
      WorkflowStateRepository workflowStateRepository,
      StepSequenceExecutor stepSequenceExecutor,
      EventRecorder eventRecorder,
      Clock clock,
      RunContextManager runContextManager,
      int maxNestingDepth,
      ToolExecutionService toolExecutionService,
      PendingToolInvocationStore pendingToolInvocationStore,
      RequirementResolver requirementResolver,
      TransitionGate transitionGate,
      RunExecutionInterceptor runExecutionInterceptor,
      GeneratedArtifactStore generatedArtifactStore) {
    this.workflowRepository = Validate.notNull(workflowRepository,
        "workflowRepository must not be null");
    this.workflowStateRepository = Validate.notNull(workflowStateRepository,
        "workflowStateRepository must not be null");
    this.stepSequenceExecutor = Validate.notNull(stepSequenceExecutor,
        "stepSequenceExecutor must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
    this.runContextManager = Validate.notNull(runContextManager,
        "runContextManager must not be null");
    this.maxNestingDepth = Validate.isGreaterThan(maxNestingDepth, 1,
        "maxNestingDepth must be at least 1").intValue();
    this.stepTreeSearcher = new StepTreeSearcher();
    this.failureSanitiser = new FailureSanitiser();
    // Tool support is optional: these are null when no ToolExecutionService is wired.
    this.toolExecutionService = toolExecutionService;
    this.pendingToolInvocationStore = pendingToolInvocationStore;
    this.toolResultApplier = new ToolResultApplier(eventRecorder);
    this.requirementResolver = Validate.notNull(requirementResolver, "requirementResolver must not be null");
    this.transitionGate = Validate.notNull(transitionGate, "transitionGate must not be null");
    this.runExecutionInterceptor = Validate.notNull(runExecutionInterceptor,
        "runExecutionInterceptor must not be null");
    this.generatedArtifactStore = Validate.notNull(generatedArtifactStore,
        "generatedArtifactStore must not be null");
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@code workflowId} is blank
   */
  @Override
  public String start(String workflowId) {
    Validate.notBlank(workflowId, "workflowId must not be blank");
    WorkflowDefinition workflow = workflowRepository.get(workflowId);
    String runId = UUID.randomUUID().toString();
    try (RunContextManager.Scope ignored = runContextManager.open(runId, workflowId, null, null)) {
      LOG.log(System.Logger.Level.INFO, "Starting workflow run workflowId={0}, runId={1}",
          workflowId, runId);
      // The definition actually retrieved for this start — not WorkflowRuntimeBuilder.build()'s
      // stale construction-time snapshot — is what is about to run; validated against the live
      // registry so a COLLECTION step reachable through any workflow reference is caught here,
      // before any run state or RUN_STARTED event exists. findAll() covers reachability through
      // every other registered workflow's own references, but a WorkflowRepository whose get() and
      // findAll() disagree (for example get() serving a fresher entry than a stale findAll() snapshot)
      // could otherwise validate a different object than the one actually driven below; overriding
      // the workflowId entry with the exact retrieved `workflow` instance guarantees that the object
      // this call is about to drive is always the one checked, never a possibly-divergent findAll()
      // copy of it.
      Map<String, WorkflowDefinition> collectionStepValidationSet =
          new HashMap<>(workflowRepository.findAll());
      collectionStepValidationSet.put(workflowId, workflow);
      START_TIME_VALIDATOR.validateNoCollectionSteps(collectionStepValidationSet);
      RequirementCheckpoint.assertNonDeferredResolved(workflow, runId, requirementResolver);
      WorkflowState state = new WorkflowState(runId, workflowId, null, clock.instant());
      workflowStateRepository.save(state);
      eventRecorder.record(runId, null, WorkflowEventType.RUN_STARTED, null, "runtime");
      drive(state, workflow);
    }
    return runId;
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalArgumentException   if the run is cancelled, not {@link WorkflowStatus#PAUSED}, or {@code runId} is
   *                                    blank
   */
  @Override
  public void continueRun(String runId, String actorId) {
    Validate.notBlank(actorId, "actorId must not be blank");
    WorkflowState state = loadState(runId);
    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        null, null)) {
      LOG.log(System.Logger.Level.INFO, "Continuing run runId={0}, currentStatus={1}", runId,
          state.getStatus());
      ensureNotCancelled(state, "continue");
      Validate.isTrue(state.getStatus() == WorkflowStatus.PAUSED,
          "Cannot continue run '%s' in status %s".formatted(runId, state.getStatus()));
      rewindLoopAwaitingMaxIterationsDecision(state);
      enterRunning(state, "continue");
      WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
      drive(state, workflow);
    }
  }

  /**
   * When the run is {@code PAUSED} because a loop reached {@code maxIterations} under
   * {@code MaxIterationsAction.AWAIT_USER} (see {@link WorkflowState#getBlueprintIdAwaitingMaxIterationsDecision()}),
   * rewinds that loop's already-completed iteration — evicting its generated-artifact bytes and clearing its state
   * via {@link WorkflowState#clearEntriesFromUid(int, java.util.Set)} — so the loop restarts at iteration one on
   * this drive instead of the resume-skip guard mistaking the already-executed iteration for still in progress and
   * re-pausing with no progress. A no-op when the run is {@code PAUSED} for a different reason (for example an
   * interceptor veto), or when the target-based rewind in {@link #retry} already discharged it. Called
   * unconditionally by both {@link #continueRun} and {@link #retry}, before either computes its own target-specific
   * rewind, because a retry target positioned after the paused loop (or with nothing recorded at or after it) would
   * otherwise leave the loop's stale bookkeeping untouched. Runs before any {@link ExecutionContext} for this drive
   * exists, so no loop iteration can be active on the call stack yet — the exclusion set is always empty.
   */
  private void rewindLoopAwaitingMaxIterationsDecision(WorkflowState state) {
    String blueprintId = state.getBlueprintIdAwaitingMaxIterationsDecision();
    if (blueprintId == null) {
      return;
    }
    int bodyStartUid = state.getLoopIterationBodyStartUid(blueprintId);
    if (bodyStartUid > 0) {
      GeneratedArtifactEviction.evictFromUid(generatedArtifactStore, state, bodyStartUid);
      state.clearEntriesFromUid(bodyStartUid, Set.of());
    }
    state.setBlueprintIdAwaitingMaxIterationsDecision(null);
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalArgumentException   if {@code stepId} is blank, the run is cancelled, status is not
   *                                    {@link WorkflowStatus#FAILED} or {@link WorkflowStatus#PAUSED}, {@code stepId}
   *                                    does not identify a top-level step in the workflow definition, or {@code runId}
   *                                    is blank
   * @throws IllegalStateException      if the target's {@code RetryPolicy} forbids operator retry
   *                                    ({@code allowRetry=false} — including the undeclared-policy
   *                                    {@code RetryPolicy.none()} default on an AGENT/SPAR step), or the shared
   *                                    {@code maxAttempts} ceiling (shared with {@code RETRY_PREVIOUS} steps targeting
   *                                    the same step) is already reached; the run is left untouched
   * @implSpec {@code retry} is a top-level-step contract: {@code stepId} must name a step that appears directly in the
   * workflow's top-level sequence. The run is repositioned at that step and the enclosing sequence is re-driven, so the
   * target and every downstream step execute again and the run finalises on the real downstream outcome. A step that
   * exists only nested inside a blueprint or sub-workflow is rejected (retry its enclosing top-level step instead).
   */
  @Override
  public void retry(String runId, String stepId, String actorId) {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(actorId, "actorId must not be blank");
    WorkflowState state = loadState(runId);
    ensureNotCancelled(state, "retry");
    Validate.isTrue(state.getStatus() == WorkflowStatus.FAILED
            || state.getStatus() == WorkflowStatus.PAUSED,
        "Cannot retry step '%s' on run '%s' in status %s"
            .formatted(stepId, runId, state.getStatus()));

    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        stepId, null)) {
      LOG.log(System.Logger.Level.INFO, "Retry requested runId={0}, stepId={1}", runId, stepId);
      WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
      StepDefinition target = resolveTopLevelRetryTarget(workflow, stepId);

      // Validates allowRetry and atomically reserves one attempt against the shared maxAttempts
      // ceiling (see RetryPolicyAttemptCounter.reserve) before any other mutation below. A step type
      // carrying no RetryPolicy (anything but AGENT/SPAR) is unrestricted — this operator verb never
      // had a governance concept for those step types. Nothing between this point and drive() below
      // can reject the retry, so a granted reservation is never left stranded against a retry that
      // did not happen.
      RetryPolicy targetRetryPolicy = retryPolicyOf(target.behaviour());
      enforceRetryPolicy(state, target, targetRetryPolicy);

      // A loop paused at maxIterations under AWAIT_USER can never itself be the retry target (a
      // BlueprintRef is not a StepDefinition), so a target positioned after such a loop — or one
      // with nothing recorded at or after it — would leave the loop's stale cursor/body-start-uid
      // untouched by the target-based rewind below. Discharge that independently of the target's
      // sequence position first, mirroring continueRun.
      rewindLoopAwaitingMaxIterationsDecision(state);

      // Reposition the run at the target: clear the target's output and everything that ran at or
      // after it, so the re-drive re-executes the target and all downstream steps rather than
      // reusing stale outputs. The rewind threshold is the EARLIEST uid found at or after the
      // target's sequence position (not the target's own latest uid): steps without recorded
      // outputs re-execute on every resume drive and take fresh, higher uids, so the target's
      // latest uid can lie past downstream state recorded on an earlier drive — rewinding from it
      // would strand that state (a gated blueprint's marker, for example) outside the cleared
      // range. Reserved (__-prefixed) context keys are preserved by clearEntriesFromUid; when
      // nothing at or after the target ever executed there is nothing to clear. Evict the captured
      // bytes for any artifact emitted at or after the threshold first, so the re-drive re-emits
      // cleanly (upsert) rather than leaving a stale capture for a path it may not re-emit. retry()
      // runs before any ExecutionContext for this drive exists, so no loop iteration can be active
      // on the call stack yet — the exclusion set is always empty.
      Integer rewindUid = earliestUidAtOrAfter(workflow, target, state);
      if (rewindUid != null) {
        GeneratedArtifactEviction.evictFromUid(generatedArtifactStore, state, rewindUid);
        state.clearEntriesFromUid(rewindUid, Set.of());
      }
      // A PAUSED run carries pending suspension state for the step it paused on; clear it so the
      // re-drive starts the target cleanly instead of re-entering the previous pause. The failure
      // details belong to the attempt being discarded — clear them too, or a re-drive that completes
      // would report COMPLETED while still carrying the dead attempt's failure reason.
      state.setPendingArtifact(null);
      state.setPendingUserPrompt(null);
      state.setRunFailure(null);
      enterRunning(state, "retry");
      state.setCurrentStepId(target.stepId());
      eventRecorder.record(runId, target.stepId(), WorkflowEventType.STEP_RETRIED, null, actorId);

      // Re-drive the enclosing top-level sequence: StepSequenceExecutor replays from the start,
      // skips steps that still have outputs, re-runs the target and its downstream continuation,
      // and finalises through the normal terminal path (COMPLETED, a pause, or FAILED).
      drive(state, workflow);
    }
  }

  /**
   * Validates {@code target}'s {@code RetryPolicy} (when it carries one) for the {@code retry(...)}
   * operator verb, and atomically reserves one attempt against it. Rejects when
   * {@code allowRetry == false}, or when the number of attempts already made against
   * {@code target}'s shared {@link RetryPolicyAttemptCounter} budget — shared with any
   * {@code RETRY_PREVIOUS} step targeting the same step — has reached {@code maxAttempts}. A step
   * type with no {@code RetryPolicy} concept (anything but {@link AgentBehaviour}/
   * {@link SparBehaviour}) is left unrestricted. The ceiling check and the reservation are one atomic
   * operation (see {@link RetryPolicyAttemptCounter#reserve}), not a separate read followed by a
   * separate increment, so two concurrent callers racing at the ceiling — whether both via
   * {@code retry()}, or one via {@code retry()} and one via a {@code RETRY_PREVIOUS} step targeting
   * the same step — can never both be granted.
   *
   * @throws IllegalStateException if the policy forbids retry, or the shared attempt cap is already
   *                                reached
   */
  private void enforceRetryPolicy(WorkflowState state, StepDefinition target, RetryPolicy policy) {
    if (policy == null) {
      return;
    }
    Validate.isTrue(policy.allowRetry(), () -> new IllegalStateException(
        "Step '%s' RetryPolicy does not allow retry (allowRetry=false)"
            .formatted(target.stepId())));
    boolean reserved = RetryPolicyAttemptCounter.reserve(state, target.stepId(), policy.maxAttempts());
    Validate.isTrue(reserved, () -> new IllegalStateException(
        ("Step '%s' RetryPolicy maxAttempts (%d) already reached (%d attempts already used across "
            + "retry() and RETRY_PREVIOUS); retry rejected")
            .formatted(target.stepId(), policy.maxAttempts(),
                RetryPolicyAttemptCounter.read(state, target.stepId()))));
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
   * Returns the earliest execution uid recorded for the retry target or anything after it in the workflow's top-level
   * sequence — descending into blueprint bodies (including their gate markers) and nested workflows — or {@code null}
   * when nothing at or after the target has ever executed. This is the rewind threshold for
   * {@link WorkflowState#clearEntriesFromUid(int, java.util.Set)}: uid order can diverge from sequence order across resume drives
   * (output-less steps re-execute and take fresh uids), so the earliest downstream uid, not the target's own latest
   * uid, bounds the state that repositioning must discard.
   *
   * <p>The walk is bounded by visited sets: programmatic definitions can bypass load-time cycle
   * validation, and while execution fails such a run cleanly at the nesting guard, this definition-graph walk would
   * otherwise recurse without limit on a cyclic {@code WORKFLOW} or blueprint reference. A node already visited is
   * skipped — mirroring the execution-time guard's intent that cycles must fail cleanly, never crash the stack.
   */
  private Integer earliestUidAtOrAfter(WorkflowDefinition workflow, StepDefinition target,
      WorkflowState state) {
    List<Executable> steps = workflow.steps();
    Set<String> visitedWorkflowIds = new HashSet<>();
    visitedWorkflowIds.add(workflow.id());
    Set<String> visitedBlueprintKeys = new HashSet<>();
    Integer min = null;
    boolean reached = false;
    for (Executable executable : steps) {
      if (!reached
          && executable instanceof StepDefinition step
          && step.stepId().equals(target.stepId())) {
        reached = true;
      }
      if (reached) {
        min = minUid(executable, workflow, state, min, visitedWorkflowIds, visitedBlueprintKeys);
      }
    }
    return min;
  }

  private Integer minUid(Executable executable, WorkflowDefinition enclosing, WorkflowState state,
      Integer min, Set<String> visitedWorkflowIds, Set<String> visitedBlueprintKeys) {
    Integer result = min;
    if (executable instanceof StepDefinition step) {
      result = lower(result, state.getStepExecutionUid().get(step.stepId()));
      if (step.behaviour() instanceof WorkflowBehaviour workflowBehaviour
          && visitedWorkflowIds.add(workflowBehaviour.workflowRef())) {
        WorkflowDefinition nested = workflowRepository.get(workflowBehaviour.workflowRef());
        if (nested != null) {
          for (Executable inner : nested.steps()) {
            result = minUid(inner, nested, state, result, visitedWorkflowIds, visitedBlueprintKeys);
          }
        }
      }
    } else if (executable instanceof BlueprintRef ref) {
      result = lower(result,
          state.getStepExecutionUid().get(TransitionGate.blueprintGateMarker(ref.blueprintId())));
      // Blueprint ids are scoped to their enclosing workflow, so the visited key carries both.
      if (visitedBlueprintKeys.add(enclosing.id() + ":" + ref.blueprintId())) {
        BlueprintDefinition blueprint = enclosing.blueprints().get(ref.blueprintId());
        if (blueprint != null) {
          for (Executable inner : blueprint.steps()) {
            result = minUid(inner, enclosing, state, result, visitedWorkflowIds,
                visitedBlueprintKeys);
          }
        }
      }
    } else if (executable instanceof WorkflowDefinition nested) {
      if (visitedWorkflowIds.add(nested.id())) {
        for (Executable inner : nested.steps()) {
          result = minUid(inner, nested, state, result, visitedWorkflowIds, visitedBlueprintKeys);
        }
      }
    }
    return result;
  }

  private static Integer lower(Integer current, Integer candidate) {
    if (candidate == null) {
      return current;
    }
    if (current == null || candidate < current) {
      return candidate;
    }
    return current;
  }

  /**
   * Resolves {@code stepId} to a top-level {@link StepDefinition} retry target. Only a step that appears directly in
   * {@code workflow.steps()} is a valid target, because {@code retry} repositions the run by re-driving the top-level
   * sequence. A step that exists only nested inside a blueprint or sub-workflow is rejected with the id of its
   * enclosing top-level entry; an unknown step is rejected as not found.
   */
  private StepDefinition resolveTopLevelRetryTarget(WorkflowDefinition workflow, String stepId) {
    StepDefinition target = stepTreeSearcher.findTopLevelStep(workflow, stepId);
    if (target != null) {
      return target;
    }
    String enclosing = stepTreeSearcher.findEnclosingTopLevelId(workflow, stepId);
    Validate.isTrue(enclosing == null,
        "Step '%s' is not a top-level retry target; retry the enclosing step '%s'".formatted(stepId, enclosing));
    throw new IllegalArgumentException(
        "Step '%s' not found in workflow '%s'".formatted(stepId, workflow.id()));
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalArgumentException   if {@code stepId} is blank, the run is cancelled, status is not
   *                                    {@link WorkflowStatus#AWAITING_APPROVAL}, or {@code runId} is blank
   * @throws IllegalStateException      if {@code stepId} does not identify the step the run is suspended on
   */
  @Override
  public void approve(String runId, String stepId, String approverNote, String actorId) {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(actorId, "actorId must not be blank");
    WorkflowState state = loadState(runId);
    ensureNotCancelled(state, "approve");
    if (state.getStatus() == WorkflowStatus.AWAITING_TOOL_APPROVAL) {
      throw new IllegalStateException(
          "Run is awaiting a tool approval; use continueAfterToolApproval instead of approve");
    }
    if (state.getStatus() == WorkflowStatus.AWAITING_TOOL_DECISION) {
      throw new IllegalStateException(
          "Run is awaiting a tool decision; use resolveToolDecision instead of approve");
    }
    Validate.isTrue(state.getStatus() == WorkflowStatus.AWAITING_APPROVAL,
        "Cannot approve run '%s' in status %s".formatted(runId, state.getStatus()));
    // Same suspended-step identity protection as submitReview/decideStepApproval: the APPROVED
    // audit event must attribute to the step the run is actually suspended on, never to an
    // arbitrary caller-supplied id.
    requireSuspendedStep(state, runId, stepId);

    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        stepId, null)) {
      String truncated = StringUtils.defaultString(approverNote)
          .substring(0, Math.min(200, StringUtils.defaultString(approverNote).length()));
      LOG.log(System.Logger.Level.INFO, "Approve requested runId={0}, stepId={1}, note={2}", runId,
          stepId, truncated);
      eventRecorder.record(runId, stepId, WorkflowEventType.APPROVED, approverNote, actorId);
      enterRunning(state, "approve");
      WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
      drive(state, workflow);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalArgumentException   if {@code answers} is {@code null}, the run is cancelled, status is not
   *                                    {@link WorkflowStatus#AWAITING_INPUT}, a pending artifact path is inconsistent
   *                                    with state, or {@code runId} is blank
   */
  @Override
  public void submitInput(String runId, Map<String, String> answers, String actorId) {
    Validate.notNull(answers, "answers must not be null");
    Validate.notBlank(actorId, "actorId must not be blank");
    WorkflowState state = loadState(runId);
    ensureNotCancelled(state, "submit input");
    Validate.isTrue(state.getStatus() == WorkflowStatus.AWAITING_INPUT,
        "Cannot submit input on run '%s' in status %s".formatted(runId, state.getStatus()));

    if (state.getPendingUserPrompt() != null) {
      handlePendingUserPrompt(runId, answers, state);
      return;
    }

    ArtifactDefinition pending = Validate.notNull(state.getPendingArtifact(),
        "Run '%s' is AWAITING_INPUT but has no pending artifact".formatted(runId));

    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        state.getCurrentStepId(), null)) {
      handleUserAnswers(runId, answers, state, pending, actorId);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalArgumentException   if {@code runId} is blank
   */
  @Override
  public WorkflowState getState(String runId) {
    WorkflowState state = loadState(runId);
    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        state.getCurrentStepId(), null)) {
      return state.snapshot();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>An {@link ApprovalDecision.Approve} whose resumed call succeeds applies the tool result and
   * advances the requesting step; an {@link ApprovalDecision.Reject} records the tool error and
   * likewise advances. An approved call that <em>fails</em> (resolution, validation, or the
   * provider itself) does not advance: the tool-execution service has already persisted a fresh
   * pending row (origin {@code EXECUTION_FAILED}), and this method re-suspends the run in
   * {@link WorkflowStatus#AWAITING_TOOL_DECISION} so the operator gets that further decision point
   * via {@code resolveToolDecision} instead of the row being orphaned against an advanced run.
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalStateException      if no tool-execution service is configured, or the run is in
   *                                    {@link WorkflowStatus#AWAITING_APPROVAL} (use {@code approve}) or
   *                                    {@link WorkflowStatus#AWAITING_TOOL_DECISION} (use {@code resolveToolDecision})
   * @throws IllegalArgumentException   if the run is cancelled, not {@link WorkflowStatus#AWAITING_TOOL_APPROVAL}, or
   *                                    an id/argument is blank or null
   * @throws com.agentforge4j.core.spi.tool.PolicyDenialTerminalException if {@code decision} is an
   *                                    {@link ApprovalDecision.Approve} against a policy-denied
   *                                    pending invocation (a denial is terminal; see
   *                                    {@code ToolExecutionService.resume})
   * @throws ToolInvocationClaimLostException if there is no claimable pending invocation for this
   *                                    id — whether because a concurrent resolution already claimed
   *                                    and resolved it before this call's own peek, or because a
   *                                    concurrent resume claimed or replaced it between this call's
   *                                    peek and its own claim attempt; no run state is mutated when
   *                                    this is thrown
   */
  @Override
  public WorkflowState continueAfterToolApproval(String runId, String toolInvocationId,
      ApprovalDecision decision) {
    validateToolResumeConfigured(runId, toolInvocationId);
    Validate.notNull(decision, "decision must not be null");

    WorkflowState state = loadState(runId);
    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        state.getCurrentStepId(), null)) {
      validateToolResumeStatus(runId, state, WorkflowStatus.AWAITING_TOOL_APPROVAL,
          "continueAfterToolApproval");
      String capability = determineCapability(runId, toolInvocationId);

      LOG.log(System.Logger.Level.INFO,
          "Continuing after tool approval runId={0}, toolInvocationId={1}, decision={2}",
          runId, toolInvocationId, decision.getClass().getSimpleName());

      ToolExecutionOutcome outcome =
          toolExecutionService.resume(runId, toolInvocationId, decision);
      String actor = approverActor(decision);
      if (outcome.status() == ToolExecutionOutcome.Status.EXECUTED) {
        toolResultApplier.apply(capability, outcome.result(), state, actor);
      } else if (outcome.status() == ToolExecutionOutcome.Status.FAILED) {
        // The approved call failed (resolution, validation, or the provider itself): the service
        // has already persisted a fresh pending row (origin EXECUTION_FAILED), so re-suspend the
        // run in AWAITING_TOOL_DECISION for a further operator decision — exactly the status that
        // row resolves. Advancing here instead would orphan that row forever (no runtime verb
        // could ever resolve it once the run moved off the tool-suspension statuses) while leaving
        // it claimable by a later direct SPI resume(Approve), re-invoking the provider for a run
        // that already recorded the error and moved on.
        state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
        persistSuspensionCancellationWins(state);
        return state.snapshot();
      } else {
        // DENIED: the operator rejected the invocation; record the tool error so downstream steps
        // can branch on it, then advance past the requesting step.
        toolResultApplier.applyError(capability, outcome.detail(), state, actor);
      }

      advancePastToolStep(state, "tool-invocation:" + outcome.status(),
          "continue after tool approval");
      return state.snapshot();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@link ToolDecision.Retry} against a pending invocation that a {@link
   * com.agentforge4j.core.spi.tool.ToolPolicy} denied is an invalid, non-executing transition: a
   * policy denial is terminal for that invocation, so the underlying resume propagates a {@link
   * com.agentforge4j.core.spi.tool.PolicyDenialTerminalException} without invoking the provider or
   * mutating run state, leaving the run suspended in {@link WorkflowStatus#AWAITING_TOOL_DECISION}
   * with its pending row intact for a later {@link ToolDecision.Continue}. A retried call that fails
   * again re-suspends the run in {@link WorkflowStatus#AWAITING_TOOL_DECISION} for a further
   * decision, rather than silently advancing without a tool result.
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalStateException      if no tool-execution service is configured, or the run is in
   *                                    {@link WorkflowStatus#AWAITING_APPROVAL} (use {@code approve} instead)
   * @throws IllegalArgumentException   if the run is cancelled, not {@link WorkflowStatus#AWAITING_TOOL_DECISION}, or
   *                                    an id is blank or the decision null
   * @throws com.agentforge4j.core.spi.tool.PolicyDenialTerminalException if {@code decision} is a
   *                                    {@link ToolDecision.Retry} against a policy-denied pending
   *                                    invocation
   * @throws ToolInvocationClaimLostException if there is no claimable pending invocation for this
   *                                    id — whether because it was already claimed and resolved by a
   *                                    concurrent resolution before this call's own peek, or because
   *                                    a concurrent resolution claimed or replaced it between this
   *                                    call's peek and its own claim attempt. For
   *                                    {@link ToolDecision.Retry} the latter is detected inside
   *                                    {@code toolExecutionService.resume}; for
   *                                    {@link ToolDecision.Continue} this method claims the row
   *                                    itself before applying anything. No run state is mutated when
   *                                    this is thrown
   */
  @Override
  public WorkflowState resolveToolDecision(String runId, String toolInvocationId,
      ToolDecision decision) {
    validateToolResumeConfigured(runId, toolInvocationId);
    Validate.notNull(decision, "decision must not be null");

    WorkflowState state = loadState(runId);
    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        state.getCurrentStepId(), null)) {
      validateToolResumeStatus(runId, state, WorkflowStatus.AWAITING_TOOL_DECISION,
          "resolveToolDecision");

      // validateToolResumeStatus just confirmed AWAITING_TOOL_DECISION, so a null find() here means
      // a concurrent resolution (another resolveToolDecision call for this same invocation) already
      // claimed and fully resolved it — including, potentially, driving the run past this status —
      // between that check and this line. This is the same benign concurrency-loss signal as a lost
      // claim deeper in either branch below, never a caller error at this point.
      PendingToolInvocation pending = pendingToolInvocationStore.find(runId, toolInvocationId);
      if (pending == null) {
        throw new ToolInvocationClaimLostException(
            "No claimable pending tool invocation '%s' for run '%s' (already claimed and resolved "
                .formatted(toolInvocationId, runId)
                + "by a concurrent resolution before this call reached it)");
      }
      String capability = pending.capability();

      LOG.log(System.Logger.Level.INFO,
          "Resolving tool decision runId={0}, toolInvocationId={1}, decision={2}",
          runId, toolInvocationId, decision.getClass().getSimpleName());

      String actorId = decision.actorId();
      if (decision instanceof ToolDecision.Retry) {
        // A policy-denied row is terminal: toolExecutionService.resume propagates
        // PolicyDenialTerminalException without invoking the provider or touching state, leaving
        // the run suspended in AWAITING_TOOL_DECISION with its pending row intact for a later,
        // legitimate Continue.
        ToolExecutionOutcome outcome = toolExecutionService.resume(
            runId, toolInvocationId, new ApprovalDecision.Approve(actorId));
        if (outcome.status() == ToolExecutionOutcome.Status.EXECUTED) {
          toolResultApplier.apply(capability, outcome.result(), state, actorId);
        } else {
          // The retried call failed again: the service has already persisted a fresh pending row
          // (origin EXECUTION_FAILED), so re-suspend for a further operator decision rather than
          // silently advancing without a tool result. This does not flow into drive(), whose
          // finally block is the usual cancellation-corrective persistence point, so persist via
          // the cancellation-aware helper: the retried provider call may have been in flight for
          // seconds, and a cancel() acknowledged during it must not be clobbered back to a
          // suspension status here.
          state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
          persistSuspensionCancellationWins(state);
          return state.snapshot();
        }
      } else {
        // Continue without the tool: atomically claim the exact row this call observed before
        // applying anything. A plain remove(runId, toolInvocationId) here would be keyed only by
        // id, so it could consume a replacement row (persisted meanwhile by a resume that failed
        // and re-suspended) under this call's stale authorization, or race a concurrent second
        // resolution (another Continue, or a Retry) that has already claimed the same row. On loss,
        // nothing is mutated: no error is applied, no row is removed.
        PendingToolInvocation claimed =
            pendingToolInvocationStore.claim(runId, toolInvocationId, pending);
        if (claimed == null) {
          throw new ToolInvocationClaimLostException(
              "Pending tool invocation '%s' for run '%s' was claimed or replaced by a concurrent "
                  .formatted(toolInvocationId, runId)
                  + "resolution between this call's peek and its own claim attempt");
        }
        toolResultApplier.applyError(capability, claimed.reason(), state, actorId);
      }

      advancePastToolStep(state, "tool-decision:" + decision.getClass().getSimpleName(),
          "resolve tool decision");
      return state.snapshot();
    }
  }

  private void advancePastToolStep(WorkflowState state, String stepOutputMarker, String operation) {
    String stepId = state.getCurrentStepId();
    if (StringUtils.isNotBlank(stepId)) {
      // Synthetic step output (not the agent's response): marks the requesting step done so the
      // drive loop advances past it without re-invoking the LLM. The real payload, if any, lives
      // in the tool.<capability> / tool.<capability>.error context keys.
      state.putStepOutput(stepId, stepOutputMarker);
    }
    enterRunning(state, operation);
    WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
    if (gateCompletedStep(state, workflow)) {
      return;
    }
    drive(state, workflow);
  }

  private static String approverActor(ApprovalDecision decision) {
    if (decision instanceof ApprovalDecision.Approve approve) {
      return approve.approvedBy();
    }
    if (decision instanceof ApprovalDecision.Reject reject) {
      return reject.rejectedBy();
    }
    throw new IllegalStateException(
        "Unexpected ApprovalDecision subtype: " + decision.getClass().getName());
  }

  /**
   * Resolves the pending invocation's capability for {@code continueAfterToolApproval}. The status
   * guard just confirmed a tool suspension, so a missing row here means a concurrent resolution
   * already claimed and fully resolved it before this call's own peek — the same benign
   * concurrency-loss signal as a lost claim deeper in the resume path, never a caller error at this
   * point (mirrors {@code resolveToolDecision}'s pre-peek classification).
   *
   * @throws ToolInvocationClaimLostException if no pending invocation matches
   */
  private String determineCapability(String runId, String toolInvocationId) {
    PendingToolInvocation pending = pendingToolInvocationStore.find(runId, toolInvocationId);
    if (pending == null) {
      throw new ToolInvocationClaimLostException(
          "No claimable pending tool invocation '%s' for run '%s' (already claimed and resolved "
              .formatted(toolInvocationId, runId)
              + "by a concurrent resolution before this call reached it)");
    }
    return pending.capability();
  }

  /**
   * Guards a tool-resume verb: the run must be in {@code required}. A run in a <em>different</em> suspension status is
   * rejected with an {@link IllegalStateException} naming the correct verb for its actual status (the three suspension
   * statuses map one-to-one to {@code approve}, {@code continueAfterToolApproval}, and {@code resolveToolDecision});
   * any other status is an {@link IllegalArgumentException}.
   */
  private static void validateToolResumeStatus(String runId, WorkflowState state,
      WorkflowStatus required, String verb) {
    ensureNotCancelled(state, verb);
    requireSuspensionStatus(state, runId, required, verb);
  }

  private static String verbFor(WorkflowStatus suspensionStatus) {
    return switch (suspensionStatus) {
      case AWAITING_APPROVAL -> "approve";
      case AWAITING_TOOL_APPROVAL -> "continueAfterToolApproval";
      case AWAITING_TOOL_DECISION -> "resolveToolDecision";
      case AWAITING_REVIEW -> "submitReview";
      case AWAITING_STEP_APPROVAL -> "decideStepApproval";
      default -> "the appropriate resume verb";
    };
  }

  private void validateToolResumeConfigured(String runId, String toolInvocationId) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(toolInvocationId, "toolInvocationId must not be blank");
    Validate.notNull(toolExecutionService,
        () -> new IllegalStateException("Tool execution service is not configured"));
    Validate.notNull(pendingToolInvocationStore,
        () -> new IllegalStateException("Pending tool invocation store is not configured"));
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalArgumentException   if status is {@link WorkflowStatus#COMPLETED} or {@link WorkflowStatus#FAILED},
   *                                    or {@code runId} is blank
   */
  @Override
  public void cancel(String runId, String actorId) {
    Validate.notBlank(actorId, "actorId must not be blank");
    WorkflowState state = loadState(runId);
    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        state.getCurrentStepId(), null)) {
      // Synchronized against finaliseDrive/failRun's own terminal-status check-then-set on the same
      // per-run monitor: whichever side observes a still-non-terminal status here or there wins the
      // transition atomically, and the loser's guard correctly sees the winner's applied change.
      synchronized (runLock(runId)) {
        WorkflowStatus status = state.getStatus();
        if (status == WorkflowStatus.CANCELLED || state.isCancellationRequested()) {
          // Idempotent repeat cancel — including the case where a concurrently-running handler
          // clobbered the CANCELLED status after the first cancel: restore it, but never record a
          // second RUN_CANCELLED.
          state.setStatus(WorkflowStatus.CANCELLED);
          return;
        }
        Validate.isTrue(status != WorkflowStatus.COMPLETED && status != WorkflowStatus.FAILED,
            "Cannot cancel run '%s' in status %s".formatted(runId, status));
        // Durable marker, distinct from the status field a concurrently-running step behaviour
        // handler may still overwrite afterward (with its own AWAITING_*/PAUSED transition) entirely
        // unsynchronized against this call — see WorkflowState.markCancellationRequested. drive()'s
        // own finalisation re-checks this marker and corrects the persisted status if a handler
        // clobbered it after this point.
        state.markCancellationRequested();
        state.setStatus(WorkflowStatus.CANCELLED);
        state.setLastUpdatedAt(clock.instant());
        workflowStateRepository.save(state);
        eventRecorder.record(runId, state.getCurrentStepId(), WorkflowEventType.RUN_CANCELLED, null,
            actorId);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalArgumentException   if {@code stepId}/{@code actorId} is blank, the run is cancelled, or the run is
   *                                    not in {@code AWAITING_REVIEW}
   * @throws IllegalStateException      if the run is in a different suspension status
   */
  @Override
  public void submitReview(String runId, String stepId, String reviewNote, String actorId) {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(actorId, "actorId must not be blank");
    WorkflowState state = loadState(runId);
    ensureNotCancelled(state, "submit review");
    requireSuspensionStatus(state, runId, WorkflowStatus.AWAITING_REVIEW, "submitReview");
    requireSuspendedStep(state, runId, stepId);
    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(), stepId, null)) {
      LOG.log(System.Logger.Level.INFO, "Review submitted runId={0}, stepId={1}", runId, stepId);
      eventRecorder.record(runId, stepId, WorkflowEventType.STEP_REVIEWED, reviewNote, actorId);
      enterRunning(state, "submit review");
      WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
      drive(state, workflow);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalArgumentException   if {@code stepId} is blank, {@code decision} is null, the run is cancelled, or
   *                                    the run is not in {@code AWAITING_STEP_APPROVAL}
   * @throws IllegalStateException      if the run is in a different suspension status
   */
  @Override
  public void decideStepApproval(String runId, String stepId, StepApprovalDecision decision) {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notNull(decision, "decision must not be null");
    WorkflowState state = loadState(runId);
    ensureNotCancelled(state, "decide step approval");
    requireSuspensionStatus(state, runId, WorkflowStatus.AWAITING_STEP_APPROVAL, "decideStepApproval");
    requireSuspendedStep(state, runId, stepId);
    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(), stepId, null)) {
      if (decision instanceof StepApprovalDecision.Approve approve) {
        handleApproved(runId, stepId, approve, state);
      } else if (decision instanceof StepApprovalDecision.Reject reject) {
        handleRejected(runId, stepId, reject, state);
      }
    }
  }

  private void handleRejected(String runId, String stepId, Reject reject, WorkflowState state) {
    LOG.log(System.Logger.Level.INFO, "Step rejected runId={0}, stepId={1}", runId, stepId);
    // The operator's rejection genuinely happened, so its audit event is recorded regardless of
    // which terminal transition wins the status below.
    eventRecorder.record(runId, stepId, WorkflowEventType.STEP_REJECTED, reject.reason(), reject.rejectedBy());
    String supportId = UUID.randomUUID().toString();
    String reason = StringUtils.defaultIfBlank(reject.reason(), "Step '%s' rejected".formatted(stepId));
    // Terminal transitions are mutually exclusive: a concurrent cancel() landing between
    // decideStepApproval's cancellation guard and this write must not be overwritten by FAILED —
    // whichever side wins the per-run lock's terminal check-and-set first is final, exactly as in
    // failRun/finaliseDrive/cancel. The loser mutates and persists nothing.
    synchronized (runLock(runId)) {
      if (!tryEnterTerminalStatus(state, WorkflowStatus.FAILED)) {
        return;
      }
      state.setRunFailure(new RunFailure.StepRejectionFailure(reason, stepId, supportId));
      state.setLastUpdatedAt(clock.instant());
      workflowStateRepository.save(state);
    }
  }

  private void handleApproved(String runId, String stepId, Approve approve, WorkflowState state) {
    LOG.log(System.Logger.Level.INFO, "Step approved runId={0}, stepId={1}", runId, stepId);
    eventRecorder.record(runId, stepId, WorkflowEventType.STEP_APPROVED, approve.note(), approve.approvedBy());
    enterRunning(state, "decide step approval");
    WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
    drive(state, workflow);
  }

  /**
   * Honours a completed step's transition gate at a {@code DefaultWorkflowRuntime}-owned completion point
   * ({@code handleUserAnswers}, {@code advancePastToolStep}). When the step suspends, the state is persisted and the
   * caller must not drive.
   *
   * @return {@code true} when the run was suspended; {@code false} when it should advance
   */
  private boolean gateCompletedStep(WorkflowState state, WorkflowDefinition workflow) {
    String stepId = state.getCurrentStepId();
    if (stepId == null) {
      return false;
    }
    // Resolve against the reachable workflow graph (root + sub-workflow frames) so a completed step
    // inside a nested sub-workflow is found, not only the root's own steps.
    StepDefinition step =
        stepTreeSearcher.findStepAcrossWorkflows(workflow, stepId, workflowRepository);
    if (step == null) {
      return false;
    }
    if (transitionGate.suspendIfGated(step, state)) {
      // Non-driving suspension write: nothing downstream re-checks cancellation on this path (no
      // drive() finally runs), so persist via the cancellation-aware helper.
      persistSuspensionCancellationWins(state);
      return true;
    }
    return false;
  }

  private static void requireSuspensionStatus(WorkflowState state, String runId, WorkflowStatus required, String verb) {
    WorkflowStatus actual = state.getStatus();
    if (actual == required) {
      return;
    }
    if (isSuspensionStatus(actual)) {
      throw new IllegalStateException("Run '%s' is in status %s; use %s instead of %s"
          .formatted(runId, actual, verbFor(actual), verb));
    }
    throw new IllegalArgumentException("Cannot %s on run '%s' in status %s".formatted(verb, runId, actual));
  }

  /**
   * Asserts that {@code stepId} identifies the step the run is actually suspended on, so the review/approval verbs
   * cannot misattribute their audit events (or a {@link RunFailure.StepRejectionFailure}) to a step other than the
   * gated one. {@link TransitionGate} canonicalises the suspended-step identity into
   * {@link WorkflowState#getCurrentStepId()} for every gate carrier (plain step, {@code WORKFLOW} carrier, and
   * blueprint — which carries its blueprint id), so a single comparison suffices. A mismatch is a state conflict, not a
   * bad argument — it uses the same {@link IllegalStateException} as a wrong-suspension-status mismatch.
   */
  private static void requireSuspendedStep(WorkflowState state, String runId, String stepId) {
    if (stepId.equals(state.getCurrentStepId())) {
      return;
    }
    throw new IllegalStateException("Run '%s' is not suspended on step '%s'".formatted(runId, stepId));
  }

  private static boolean isSuspensionStatus(WorkflowStatus status) {
    return status == WorkflowStatus.AWAITING_APPROVAL
        || status == WorkflowStatus.AWAITING_TOOL_APPROVAL
        || status == WorkflowStatus.AWAITING_TOOL_DECISION
        || status == WorkflowStatus.AWAITING_REVIEW
        || status == WorkflowStatus.AWAITING_STEP_APPROVAL;
  }

  private static void writePromptAnswerToContext(WorkflowState state,
      Map<String, String> answers) {
    String answer = answers.values().stream().findFirst().orElse("");

    state.putContextValue("user.response." + state.getCurrentStepId(),
        new StringContextValue(answer, ContextProvenance.USER_SUPPLIED)
    );
  }

  private void handleUserAnswers(String runId, Map<String, String> answers, WorkflowState state,
      ArtifactDefinition pending, String actorId) {
    LOG.log(System.Logger.Level.INFO, "Submitting input runId={0}, keys={1}", runId,
        answers.keySet());
    WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
    writeAnswersToContext(state, pending, answers,
        currentStepOutputKeys(workflow, state.getCurrentStepId()));
    eventRecorder.record(runId, state.getCurrentStepId(),
        WorkflowEventType.CONTEXT_UPDATED,
        "submitted input for artifact " + pending.id(), actorId);

    state.setPendingArtifact(null);
    enterRunning(state, "submit input");

    if (state.getCurrentStepId() != null) {
      state.putStepOutput(state.getCurrentStepId(), "submitted");
    }

    LOG.log(System.Logger.Level.INFO,
        "After submitInput runId={0}, currentStepId={1}, status={2}, pendingArtifact={3}, stepOutputs={4}, contextKeys={5}",
        runId,
        state.getCurrentStepId(),
        state.getStatus(),
        state.getPendingArtifact() == null ? null : state.getPendingArtifact().id(),
        state.getStepOutputs().keySet(),
        state.getContext().keySet());
    if (gateCompletedStep(state, workflow)) {
      return;
    }
    drive(state, workflow);
  }

  private void handlePendingUserPrompt(String runId, Map<String, String> answers,
      WorkflowState state) {
    WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        state.getCurrentStepId(), null)) {
      writePromptAnswerToContext(state, answers);
      state.setPendingUserPrompt(null);
      enterRunning(state, "submit input");
      drive(state, workflow);
    }
  }

  private WorkflowState loadState(String runId) {
    Validate.notBlank(runId, "runId must not be blank");
    return workflowStateRepository.findById(runId)
        .orElseThrow(() -> {
          LOG.log(System.Logger.Level.ERROR, "Run not found runId={0}", runId);
          return new ExecutionNotFoundException(runId);
        });
  }

  private void drive(WorkflowState state, WorkflowDefinition workflow) {
    // Scope artifact capture to the root workflow's reachable VALIDATE-declared paths before any step runs.
    // Idempotent and re-applied on every drive (start / continue / retry), so a resume stays populated.
    state.mergeCapturedArtifactPaths(WorkflowCapturePathCollector.collect(workflow));
    if (state.getStepExecutionUid().isEmpty()) {
      // First entry into main execution (on resume at least one step has already executed). A
      // registered interceptor may throw ExecutionBlockedException here to block before any step
      // runs; it is raised outside the try below so it propagates cleanly rather than failing the run.
      // The interceptor receives a defensive snapshot, so it cannot mutate live run state.
      try {
        runExecutionInterceptor.beforeMainExecution(new RunExecutionContext(state.getRunId(), state.snapshot()));
      } catch (ExecutionBlockedException blocked) {
        recordRunBlocked(state, null);
        // This catch sits outside the try/finally below, so it must persist the transition itself
        // — nothing downstream saves the state on this path. Persist via the cancellation-aware
        // helper: a concurrent cancel() landing during the interceptor veto must keep its durable
        // CANCELLED status, never be clobbered back to a resumable PAUSED by a bare save.
        persistSuspensionCancellationWins(state);
        throw blocked;
      }
    }
    ExecutionContext executionContext = newExecutionContext(state, workflow);
    executionContext.enterWorkflow(workflow);
    try {
      ExecutionOutcome outcome = stepSequenceExecutor.executeAll(workflow.steps(),
          executionContext);
      finaliseDrive(state, outcome);
    } catch (ExecutionBlockedException blocked) {
      // A deliberate control veto (e.g. budget block) — record a neutral block event and propagate.
      // recordRunBlocked marks the run PAUSED (resumable); the finally below persists it. Never failRun.
      recordRunBlocked(state, state.getCurrentStepId());
      throw blocked;
    } catch (RuntimeException throwable) {
      failRun(state, state.getCurrentStepId(), throwable);
    } finally {
      executionContext.exitWorkflow();
      enforceCancellationWon(state);
      workflowStateRepository.save(state);
      // Release the run's transient generated-artifact bytes once it reaches a terminal state; the
      // content-free descriptors on the persisted state remain the durable record. A paused run keeps
      // its artifacts so a same-drive resume can still read them.
      if (isTerminal(state.getStatus())) {
        generatedArtifactStore.clear(state.getRunId());
      }
    }
  }

  /**
   * Final, unconditional corrective pass, run at the very end of every drive before the state is
   * persisted: a concurrent {@link #cancel} may have durably marked this run cancelled (see
   * {@link WorkflowState#markCancellationRequested}) while a step behaviour handler, entirely
   * unsynchronized against that call, later overwrote {@code state}'s own status field with its own
   * suspension transition (for example {@code AWAITING_INPUT} for a {@code PAUSED} outcome) —
   * clobbering the {@code CANCELLED} value {@code cancel()} set. {@link #finaliseDrive} and
   * {@link #failRun} only ever guard against this for the {@code COMPLETED}/{@code FAILED} outcomes
   * they themselves decide; nothing guards a bare {@code PAUSED} outcome, since the fine-grained
   * suspension status there is deliberately left to whichever handler set it. Cancellation always
   * wins regardless of outcome: if it was requested and the persisted status is not already
   * {@code CANCELLED}, this restores it, under the same per-run lock {@link #cancel} itself uses, so
   * this correction can never itself race a concurrent {@code cancel()} call. {@code RUN_CANCELLED}
   * was already recorded once by {@code cancel()}; only the status field needs correcting here, not
   * a second event.
   */
  private void enforceCancellationWon(WorkflowState state) {
    synchronized (runLock(state.getRunId())) {
      if (state.isCancellationRequested() && state.getStatus() != WorkflowStatus.CANCELLED) {
        state.setStatus(WorkflowStatus.CANCELLED);
        state.setLastUpdatedAt(clock.instant());
      }
    }
  }

  /**
   * Persists a non-driving suspension transition — one that never flows into {@link #drive}, whose
   * {@code finally} block is the usual cancellation-corrective persistence point — under the
   * per-run lock, letting cancellation win: a concurrent {@link #cancel} may have durably marked
   * this run cancelled (see {@link WorkflowState#markCancellationRequested}) while this thread was
   * still off-lock (for example during an in-flight retried provider call), and its
   * {@code CANCELLED} status must never be clobbered back to a suspension status by this later
   * write. {@code RUN_CANCELLED} was already recorded once by {@code cancel()} itself; only the
   * status field needs restoring here, never a second event. The non-cancelled case persists the
   * suspension status the caller just set, unchanged.
   */
  private void persistSuspensionCancellationWins(WorkflowState state) {
    synchronized (runLock(state.getRunId())) {
      if (state.isCancellationRequested()) {
        state.setStatus(WorkflowStatus.CANCELLED);
      }
      state.setLastUpdatedAt(clock.instant());
      workflowStateRepository.save(state);
    }
  }

  /**
   * Attempts to transition {@code state} into the terminal {@code target} status, but only if it has
   * not already reached any terminal status ({@link WorkflowStatus#CANCELLED},
   * {@link WorkflowStatus#COMPLETED}, or {@link WorkflowStatus#FAILED}) — whichever of
   * {@link #cancel}, {@link #finaliseDrive}'s {@code COMPLETED}/{@code FAILED} arms, or
   * {@link #failRun} first reaches this check wins, and every later caller for the same run is a
   * no-op that mutates and records nothing. Callers must hold this run's own {@link #runLock} for the
   * whole check-and-set (and any event recording contingent on it).
   *
   * <p>A durably requested cancellation (see {@link WorkflowState#markCancellationRequested}) also
   * loses this check even when the {@code CANCELLED} status itself was clobbered by an
   * unsynchronized transition in between: {@code RUN_CANCELLED} is already the run's terminal
   * record, so no caller may record a contradictory {@code RUN_COMPLETED}/{@code RUN_FAILED} after
   * it. The clobbered status is restored here — at the decision point, before any event is
   * recorded — rather than corrected after the fact.
   *
   * @return {@code true} if this call's transition was applied (this call won); {@code false} if the
   * run had already reached a (possibly different) terminal status, or cancellation was requested
   */
  private static boolean tryEnterTerminalStatus(WorkflowState state, WorkflowStatus target) {
    if (isTerminal(state.getStatus())) {
      return false;
    }
    if (state.isCancellationRequested()) {
      state.setStatus(WorkflowStatus.CANCELLED);
      return false;
    }
    state.setStatus(target);
    return true;
  }

  private static boolean isTerminal(WorkflowStatus status) {
    return status == WorkflowStatus.COMPLETED
        || status == WorkflowStatus.FAILED
        || status == WorkflowStatus.CANCELLED;
  }

  /**
   * Records a neutral {@link WorkflowEventType#RUN_BLOCKED} audit event when a registered interceptor vetoes the run,
   * and marks the run {@link WorkflowStatus#PAUSED} so the embedding application can resume it via {@code continueRun}
   * once the block is resolved (the vetoing condition lifted) — or cancel it. The {@code RUN_BLOCKED} event is the
   * durable record of <em>why</em> the run paused; OSS performs no terminal transition and sets no {@link RunFailure}.
   */
  private void recordRunBlocked(WorkflowState state, String stepId) {
    // Cancellation-aware under the per-run lock: a concurrent cancel() that already durably
    // recorded RUN_CANCELLED must not be clobbered to a resumable PAUSED, and no RUN_BLOCKED event
    // may follow the run's terminal RUN_CANCELLED record.
    synchronized (runLock(state.getRunId())) {
      if (state.isCancellationRequested() || state.getStatus() == WorkflowStatus.CANCELLED) {
        state.setStatus(WorkflowStatus.CANCELLED);
        state.setLastUpdatedAt(clock.instant());
        return;
      }
      state.setStatus(WorkflowStatus.PAUSED);
      state.setLastUpdatedAt(clock.instant());
      eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.RUN_BLOCKED, null, "runtime");
    }
  }

  private ExecutionContext newExecutionContext(WorkflowState state, WorkflowDefinition workflow) {
    return new ExecutionContext(state, workflow, maxNestingDepth);
  }

  private void finaliseDrive(WorkflowState state, ExecutionOutcome outcome) {
    state.setLastUpdatedAt(clock.instant());
    switch (outcome) {
      case COMPLETED -> {
        // Synchronized against cancel()'s and failRun()'s own check-then-set on the same per-run
        // lock: a concurrent cancel() may transition the run to CANCELLED (and record RUN_CANCELLED)
        // while the last step was still executing on this drive, and a concurrent failure on another
        // overlapping drive of the same run (a caller-side contract violation this still defends
        // against) may transition it to FAILED — either winning first must make this arm a no-op.
        // tryEnterTerminalStatus makes exactly one of any racing cancel/complete/fail set win the
        // terminal status atomically; the loser here records nothing.
        synchronized (runLock(state.getRunId())) {
          if (tryEnterTerminalStatus(state, WorkflowStatus.COMPLETED)) {
            eventRecorder.record(state.getRunId(), null,
                WorkflowEventType.RUN_COMPLETED, null, "runtime");
          }
        }
      }
      case PAUSED -> {
        // Handlers have already set a fine-grained status (AWAITING_INPUT /
        // AWAITING_APPROVAL / PAUSED) — do not overwrite it here. enforceCancellationWon (in drive()'s
        // finally block) still corrects this afterward if a concurrent cancel() was clobbered by that
        // handler's own unsynchronized status write.
      }
      case FAILED -> {
        // A handler (e.g. MaxIterationsHandler on MaxIterationsAction.FAIL) may already have
        // transitioned the run to FAILED and recorded its own RUN_FAILED event with a specific
        // payload; tryEnterTerminalStatus's terminal-status guard (any of CANCELLED/COMPLETED/FAILED)
        // makes this generic fallback a no-op in that case, never double-firing RUN_FAILED for the
        // same terminal failure. The FAILED half of that guard is currently unreachable in practice
        // (the only present ExecutionOutcome.FAILED producer, MaxIterationsHandler.handleFailed,
        // always sets FAILED and records RUN_FAILED itself first); it is kept as defense-in-depth
        // against a future FAILED-outcome producer that does not self-record RUN_FAILED. Synchronized
        // against cancel()/failRun() for the same reason as the COMPLETED arm above.
        synchronized (runLock(state.getRunId())) {
          if (tryEnterTerminalStatus(state, WorkflowStatus.FAILED)) {
            eventRecorder.record(state.getRunId(), state.getCurrentStepId(),
                WorkflowEventType.RUN_FAILED, null, "runtime");
          }
        }
      }
    }
  }

  private void failRun(WorkflowState state, String failedStepId, RuntimeException throwable) {
    // Synchronized against cancel()/finaliseDrive() for the same reason as finaliseDrive's terminal
    // arms: whichever of a concurrent cancel(), a concurrent completion, or this failure reaches the
    // terminal-status guard first wins; every other one is a no-op.
    synchronized (runLock(state.getRunId())) {
      if (!tryEnterTerminalStatus(state, WorkflowStatus.FAILED)) {
        String supportId = UUID.randomUUID().toString();
        LOG.log(System.Logger.Level.INFO,
            "Throwable during already-terminal run (status={0}) suppressed runId={1} supportId={2}",
            state.getStatus(), state.getRunId(), supportId);
        LOG.log(System.Logger.Level.DEBUG,
            "Already-terminal run throwable detail supportId=" + supportId, throwable);
        return;
      }
      String supportId = UUID.randomUUID().toString();
      state.setRunFailure(new RunFailure.ExceptionFailure(
          failureSanitiser.sanitiseFailureReason(failedStepId, throwable),
          failedStepId,
          supportId));
      state.setLastUpdatedAt(clock.instant());

      LOG.log(System.Logger.Level.ERROR,
          "Run {0} failed at step {1} (supportId={2})",
          state.getRunId(),
          failedStepId,
          supportId);
      LOG.log(System.Logger.Level.ERROR, "Run failure stacktrace supportId=" + supportId, throwable);

      eventRecorder.record(
          state.getRunId(),
          failedStepId,
          WorkflowEventType.RUN_FAILED,
          "Run failed. supportId=%s, reason=%s".formatted(supportId,
              failureSanitiser.safeFailureReason(throwable)),
          "runtime");
    }
  }

  private static void ensureNotCancelled(WorkflowState state, String operation) {
    Validate.isTrue(state.getStatus() != WorkflowStatus.CANCELLED
            && !state.isCancellationRequested(),
        "Cannot %s run '%s' because it is CANCELLED"
            .formatted(operation, state.getRunId()));
  }

  /**
   * Transitions the run to {@link WorkflowStatus#RUNNING} for a resume verb, atomically re-checking
   * cancellation under the per-run lock. Every resume verb's own status guard runs off-lock, so a
   * concurrent {@link #cancel} can land between that guard and the {@code RUNNING} write — and must
   * win: the resume is rejected here with the same {@code IllegalArgumentException} the verb's
   * off-lock guard uses, instead of the {@code RUNNING} write clobbering the durable
   * {@code CANCELLED} status (which would let a later {@link #finaliseDrive} record a terminal
   * lifecycle event after {@code RUN_CANCELLED}). Checks the durable
   * {@link WorkflowState#isCancellationRequested} marker as well as the status field, so a
   * cancellation whose status write was itself already clobbered still wins.
   *
   * @throws IllegalArgumentException if the run has been cancelled
   */
  private void enterRunning(WorkflowState state, String operation) {
    synchronized (runLock(state.getRunId())) {
      ensureNotCancelled(state, operation);
      state.setStatus(WorkflowStatus.RUNNING);
      state.setLastUpdatedAt(clock.instant());
    }
  }

  private static void writeAnswersToContext(WorkflowState state,
      ArtifactDefinition pending,
      Map<String, String> answers,
      List<String> outputKeys) {
    for (Map.Entry<String, String> entry : answers.entrySet()) {
      Validate.notBlank(entry.getKey(), "answer key must not be blank");
      // Write-path defense for the reserved runtime namespace, ahead of any write or clear:
      // ContextMapping rejects reserved outputKeys and ArtifactDefinition rejects reserved ids at
      // construction, but the answer keys themselves are raw end-user input — a reserved key must
      // never reach the bare-key write/clear below (retry-attempt counters, token totals, ...) no
      // matter what a workflow definition declares.
      Validate.isTrue(!ReservedContextKeys.isReserved(entry.getKey()),
          "answer key must not use the reserved '%s' namespace: %s"
              .formatted(ReservedContextKeys.RESERVED_PREFIX, entry.getKey()));
    }
    for (Map.Entry<String, String> entry : answers.entrySet()) {
      String namespacedKey = "%s.%s".formatted(pending.id(), entry.getKey());
      boolean declaredOutput = outputKeys.contains(entry.getKey());
      String value = entry.getValue();
      if (value == null) {
        state.removeContextValue(namespacedKey);
        if (declaredOutput) {
          state.removeContextValue(entry.getKey());
        }
        continue;
      }
      state.putContextValue(namespacedKey,
          new StringContextValue(value, ContextProvenance.USER_SUPPLIED));
      // Honour the INPUT step's declared outputKeys: when an answer's item id is a declared output
      // key, also expose the value under that bare key so downstream steps and branches that read
      // the declared key resolve it. The namespaced artifactId.itemId form is kept for callers that
      // rely on it. The value carries the same USER_SUPPLIED provenance as the namespaced write.
      if (declaredOutput) {
        state.putContextValue(entry.getKey(),
            new StringContextValue(value, ContextProvenance.USER_SUPPLIED));
      }
    }
  }

  /**
   * Returns the declared output keys of the run's current step, or an empty list when the step
   * cannot be resolved or declares no context mapping.
   *
   * <p>The step is resolved against the reachable workflow graph — the root workflow and any
   * sub-workflow frames reachable through {@code WORKFLOW} steps — so a paused INPUT step inside a
   * nested sub-workflow still has its declared {@code outputKeys} found and projected, not only the
   * root workflow's steps.
   */
  private List<String> currentStepOutputKeys(WorkflowDefinition workflow, String currentStepId) {
    if (currentStepId == null) {
      return List.of();
    }
    StepDefinition step =
        stepTreeSearcher.findStepAcrossWorkflows(workflow, currentStepId, workflowRepository);
    if (step != null && step.contextMapping() != null) {
      return step.contextMapping().outputKeys();
    }
    return List.of();
  }
}
