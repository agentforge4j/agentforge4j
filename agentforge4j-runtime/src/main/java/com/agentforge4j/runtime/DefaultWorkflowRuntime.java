// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

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
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.RequirementCheckpoint;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.execution.TransitionGate;
import com.agentforge4j.runtime.interceptor.ExecutionBlockedException;
import com.agentforge4j.runtime.interceptor.RunExecutionContext;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.tool.ToolResultApplier;
import com.agentforge4j.util.Validate;
import java.time.Clock;
import java.util.List;
import java.util.Map;
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
      RunExecutionInterceptor runExecutionInterceptor) {
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
      state.setStatus(WorkflowStatus.RUNNING);
      state.setLastUpdatedAt(clock.instant());
      WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
      drive(state, workflow);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalArgumentException   if {@code stepId} is blank, the run is cancelled, status is not
   *                                    {@link WorkflowStatus#FAILED} or {@link WorkflowStatus#PAUSED}, {@code stepId}
   *                                    does not identify a top-level step in the workflow definition, or {@code runId}
   *                                    is blank
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

      // Reposition the run at the target: clear the target's output and everything that ran at or
      // after it, so the re-drive re-executes the target and all downstream steps rather than
      // reusing stale outputs. Reserved (__-prefixed) context keys are preserved by
      // clearEntriesFromUid; a target that never executed has no uid and nothing to clear.
      state.getStepExecutionUid(target.stepId()).ifPresent(state::clearEntriesFromUid);
      // A PAUSED run carries pending suspension state for the step it paused on; clear it so the
      // re-drive starts the target cleanly instead of re-entering the previous pause.
      state.setPendingArtifact(null);
      state.setPendingUserPrompt(null);
      state.setStatus(WorkflowStatus.RUNNING);
      state.setCurrentStepId(target.stepId());
      state.setLastUpdatedAt(clock.instant());
      eventRecorder.record(runId, target.stepId(), WorkflowEventType.STEP_RETRIED, null, actorId);

      // Re-drive the enclosing top-level sequence: StepSequenceExecutor replays from the start,
      // skips steps that still have outputs, re-runs the target and its downstream continuation,
      // and finalises through the normal terminal path (COMPLETED, a pause, or FAILED).
      drive(state, workflow);
    }
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

    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        stepId, null)) {
      String truncated = StringUtils.defaultString(approverNote)
          .substring(0, Math.min(200, StringUtils.defaultString(approverNote).length()));
      LOG.log(System.Logger.Level.INFO, "Approve requested runId={0}, stepId={1}, note={2}", runId,
          stepId, truncated);
      eventRecorder.record(runId, stepId, WorkflowEventType.APPROVED, approverNote, actorId);
      state.setStatus(WorkflowStatus.RUNNING);
      state.setLastUpdatedAt(clock.instant());
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
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalStateException      if no tool-execution service is configured, or the run is in
   *                                    {@link WorkflowStatus#AWAITING_APPROVAL} (use {@code approve}) or
   *                                    {@link WorkflowStatus#AWAITING_TOOL_DECISION} (use {@code resolveToolDecision})
   * @throws IllegalArgumentException   if the run is cancelled, not {@link WorkflowStatus#AWAITING_TOOL_APPROVAL}, no
   *                                    pending invocation matches, or an id/argument is blank or null
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
      } else {
        // Rejected (or a failed resume): record the tool error so downstream steps can branch on it.
        toolResultApplier.applyError(capability, outcome.detail(), state, actor);
      }

      advancePastToolStep(state, "tool-invocation:" + outcome.status());
      return state.snapshot();
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ExecutionNotFoundException if no state exists for {@code runId}
   * @throws IllegalStateException      if no tool-execution service is configured, or the run is in
   *                                    {@link WorkflowStatus#AWAITING_APPROVAL} (use {@code approve} instead)
   * @throws IllegalArgumentException   if the run is cancelled, not {@link WorkflowStatus#AWAITING_TOOL_DECISION}, no
   *                                    pending invocation matches, or an id is blank or the decision null
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

      PendingToolInvocation pending = Validate.notNull(
          pendingToolInvocationStore.find(runId, toolInvocationId),
          () -> new IllegalArgumentException(
              "No pending tool invocation '%s' for run '%s'".formatted(toolInvocationId, runId)));
      String capability = pending.capability();

      LOG.log(System.Logger.Level.INFO,
          "Resolving tool decision runId={0}, toolInvocationId={1}, decision={2}",
          runId, toolInvocationId, decision.getClass().getSimpleName());

      String actorId = decision.actorId();
      if (decision instanceof ToolDecision.Retry) {
        ToolExecutionOutcome outcome = toolExecutionService.resume(
            runId, toolInvocationId, new ApprovalDecision.Approve(actorId));
        if (outcome.status() == ToolExecutionOutcome.Status.EXECUTED) {
          toolResultApplier.apply(capability, outcome.result(), state, actorId);
        } else {
          toolResultApplier.applyError(capability, outcome.detail(), state, actorId);
        }
      } else {
        // Continue without the tool: surface the recorded reason and drop the pending row.
        toolResultApplier.applyError(capability, pending.reason(), state, actorId);
        pendingToolInvocationStore.remove(runId, toolInvocationId);
      }

      advancePastToolStep(state, "tool-decision:" + decision.getClass().getSimpleName());
      return state.snapshot();
    }
  }

  private void advancePastToolStep(WorkflowState state, String stepOutputMarker) {
    String stepId = state.getCurrentStepId();
    if (StringUtils.isNotBlank(stepId)) {
      // Synthetic step output (not the agent's response): marks the requesting step done so the
      // drive loop advances past it without re-invoking the LLM. The real payload, if any, lives
      // in the tool.<capability> / tool.<capability>.error context keys.
      state.putStepOutput(stepId, stepOutputMarker);
    }
    state.setStatus(WorkflowStatus.RUNNING);
    state.setLastUpdatedAt(clock.instant());
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

  private String determineCapability(String runId, String toolInvocationId) {
    PendingToolInvocation pending = Validate.notNull(
        pendingToolInvocationStore.find(runId, toolInvocationId),
        () -> new IllegalArgumentException(
            "No pending tool invocation '%s' for run '%s'".formatted(toolInvocationId, runId)));
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
      WorkflowStatus status = state.getStatus();
      if (status == WorkflowStatus.CANCELLED) {
        return;
      }
      Validate.isTrue(status != WorkflowStatus.COMPLETED && status != WorkflowStatus.FAILED,
          "Cannot cancel run '%s' in status %s".formatted(runId, status));
      state.setStatus(WorkflowStatus.CANCELLED);
      state.setLastUpdatedAt(clock.instant());
      workflowStateRepository.save(state);
      eventRecorder.record(runId, state.getCurrentStepId(), WorkflowEventType.RUN_CANCELLED, null, actorId);
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
      state.setStatus(WorkflowStatus.RUNNING);
      state.setLastUpdatedAt(clock.instant());
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
    eventRecorder.record(runId, stepId, WorkflowEventType.STEP_REJECTED, reject.reason(), reject.rejectedBy());
    String supportId = UUID.randomUUID().toString();
    String reason = StringUtils.defaultIfBlank(reject.reason(), "Step '%s' rejected".formatted(stepId));
    state.setStatus(WorkflowStatus.FAILED);
    state.setRunFailure(new RunFailure.StepRejectionFailure(reason, stepId, supportId));
    state.setLastUpdatedAt(clock.instant());
    workflowStateRepository.save(state);
  }

  private void handleApproved(String runId, String stepId, Approve approve, WorkflowState state) {
    LOG.log(System.Logger.Level.INFO, "Step approved runId={0}, stepId={1}", runId, stepId);
    eventRecorder.record(runId, stepId, WorkflowEventType.STEP_APPROVED, approve.note(), approve.approvedBy());
    state.setStatus(WorkflowStatus.RUNNING);
    state.setLastUpdatedAt(clock.instant());
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
      state.setLastUpdatedAt(clock.instant());
      workflowStateRepository.save(state);
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
    state.setStatus(WorkflowStatus.RUNNING);
    state.setLastUpdatedAt(clock.instant());

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
      state.setStatus(WorkflowStatus.RUNNING);
      state.setLastUpdatedAt(clock.instant());
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
    if (state.getStepExecutionUid().isEmpty()) {
      // First entry into main execution (on resume at least one step has already executed). A
      // registered interceptor may throw ExecutionBlockedException here to block before any step
      // runs; it is raised outside the try below so it propagates cleanly rather than failing the run.
      // The interceptor receives a defensive snapshot, so it cannot mutate live run state.
      try {
        runExecutionInterceptor.beforeMainExecution(new RunExecutionContext(state.getRunId(), state.snapshot()));
      } catch (ExecutionBlockedException blocked) {
        recordRunBlocked(state, null);
        // This catch sits outside the try/finally below, so it must persist the PAUSED
        // transition itself — nothing downstream saves the state on this path.
        workflowStateRepository.save(state);
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
      workflowStateRepository.save(state);
    }
  }

  /**
   * Records a neutral {@link WorkflowEventType#RUN_BLOCKED} audit event when a registered interceptor vetoes the run,
   * and marks the run {@link WorkflowStatus#PAUSED} so the embedding application can resume it via {@code continueRun}
   * once the block is resolved (credits restored, policy lifted) — or cancel it. The {@code RUN_BLOCKED} event is the
   * durable record of <em>why</em> the run paused; OSS performs no terminal transition and sets no {@link RunFailure}.
   */
  private void recordRunBlocked(WorkflowState state, String stepId) {
    state.setStatus(WorkflowStatus.PAUSED);
    state.setLastUpdatedAt(clock.instant());
    eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.RUN_BLOCKED, null, "runtime");
  }

  private ExecutionContext newExecutionContext(WorkflowState state, WorkflowDefinition workflow) {
    return new ExecutionContext(state, workflow, maxNestingDepth);
  }

  private void finaliseDrive(WorkflowState state, ExecutionOutcome outcome) {
    state.setLastUpdatedAt(clock.instant());
    switch (outcome) {
      case COMPLETED -> {
        state.setStatus(WorkflowStatus.COMPLETED);
        eventRecorder.record(state.getRunId(), null,
            WorkflowEventType.RUN_COMPLETED, null, "runtime");
      }
      case PAUSED -> {
        // Handlers have already set a fine-grained status (AWAITING_INPUT /
        // AWAITING_APPROVAL / PAUSED) — do not overwrite it here.
      }
      case FAILED -> {
        if (state.getStatus() != WorkflowStatus.CANCELLED) {
          state.setStatus(WorkflowStatus.FAILED);
          eventRecorder.record(state.getRunId(), state.getCurrentStepId(),
              WorkflowEventType.RUN_FAILED, null, "runtime");
        }
      }
    }
  }

  private void failRun(WorkflowState state, String failedStepId, RuntimeException throwable) {
    if (state.getStatus() == WorkflowStatus.CANCELLED) {
      String supportId = UUID.randomUUID().toString();
      LOG.log(System.Logger.Level.INFO,
          "Throwable during cancelled run suppressed runId={0} supportId={1}",
          state.getRunId(), supportId);
      LOG.log(System.Logger.Level.DEBUG,
          "Cancelled run throwable detail supportId=" + supportId, throwable);
      return;
    }
    String supportId = UUID.randomUUID().toString();
    state.setStatus(WorkflowStatus.FAILED);
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

  private static void ensureNotCancelled(WorkflowState state, String operation) {
    Validate.isTrue(state.getStatus() != WorkflowStatus.CANCELLED,
        "Cannot %s run '%s' because it is CANCELLED"
            .formatted(operation, state.getRunId()));
  }

  private static void writeAnswersToContext(WorkflowState state,
      ArtifactDefinition pending,
      Map<String, String> answers,
      List<String> outputKeys) {
    for (Map.Entry<String, String> entry : answers.entrySet()) {
      Validate.notBlank(entry.getKey(), "answer key must not be blank");
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
