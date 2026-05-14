package com.agentforge4j.runtime;

import com.agentforge4j.core.exception.ExecutionNotFoundException;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
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
 * {@link WorkflowState}, delegates body execution to {@link StepSequenceExecutor}, reacts to pauses
 * and completions by updating the state and emitting events, and persists state through the
 * injected repository after each drive.
 *
 * <p>Not thread-safe per run — a single run is driven by the caller's thread at
 * any given time. The embedding application is responsible for preventing concurrent drives of the
 * same run.
 *
 * <p>Construction is package-private and goes through {@link WorkflowRuntimeBuilder}. The
 * constructors take internal collaborators ({@link StepSequenceExecutor},
 * {@link ExecutableExecutor}) from the non-exported {@code com.agentforge4j.runtime.execution}
 * package, so they must not be part of the exported public API.
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
  private final ExecutableExecutor executableExecutor;
  private final EventRecorder eventRecorder;
  private final Clock clock;
  private final RunContextManager runContextManager;
  private final int maxNestingDepth;

  DefaultWorkflowRuntime(WorkflowRepository workflowRepository,
      WorkflowStateRepository workflowStateRepository,
      StepSequenceExecutor stepSequenceExecutor,
      ExecutableExecutor executableExecutor,
      EventRecorder eventRecorder,
      Clock clock,
      RunContextManager runContextManager,
      int maxNestingDepth) {
    this.workflowRepository = Validate.notNull(workflowRepository,
        "workflowRepository must not be null");
    this.workflowStateRepository = Validate.notNull(workflowStateRepository,
        "workflowStateRepository must not be null");
    this.stepSequenceExecutor = Validate.notNull(stepSequenceExecutor,
        "stepSequenceExecutor must not be null");
    this.executableExecutor = Validate.notNull(executableExecutor,
        "executableExecutor must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
    this.runContextManager = Validate.notNull(runContextManager,
        "runContextManager must not be null");
    this.maxNestingDepth = Validate.isGreaterThan(maxNestingDepth, 1,
        "maxNestingDepth must be at least 1").intValue();
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
   * @throws com.agentforge4j.core.exception.ExecutionNotFoundException if no state exists for
   *                                                                   {@code runId}
   * @throws IllegalArgumentException                                  if the run is cancelled, not
   *                                                                   {@link com.agentforge4j.core.workflow.state.WorkflowStatus#PAUSED},
   *                                                                   or {@code runId} is blank
   */
  @Override
  public void continueRun(String runId) {
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
   * @throws com.agentforge4j.core.exception.ExecutionNotFoundException if no state exists for
   *                                                                   {@code runId}
   * @throws IllegalArgumentException                                  if {@code stepId} is blank,
   *                                                                   the run is cancelled, status
   *                                                                   is not {@link com.agentforge4j.core.workflow.state.WorkflowStatus#FAILED}
   *                                                                   or {@link com.agentforge4j.core.workflow.state.WorkflowStatus#PAUSED},
   *                                                                   {@code stepId} is not present
   *                                                                   in the workflow definition, or
   *                                                                   {@code runId} is blank
   */
  @Override
  public void retry(String runId, String stepId) {
    Validate.notBlank(stepId, "stepId must not be blank");
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
      Executable target = findStep(workflow, stepId);
      eventRecorder.record(runId, stepId, WorkflowEventType.STEP_RETRIED, null, "runtime");

      state.setStatus(WorkflowStatus.RUNNING);
      state.setCurrentStepId(stepId);
      state.setLastUpdatedAt(clock.instant());

      ExecutionContext executionContext = newExecutionContext(state, workflow);
      executionContext.enterWorkflow(workflow);
      try {
        ExecutionOutcome outcome = executableExecutor.execute(target, executionContext);
        finaliseDrive(state, outcome);
      } catch (RuntimeException throwable) {
        failRun(state, stepId, throwable);
      } finally {
        executionContext.exitWorkflow();
        workflowStateRepository.save(state);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws com.agentforge4j.core.exception.ExecutionNotFoundException if no state exists for
   *                                                                   {@code runId}
   * @throws IllegalArgumentException                                  if {@code stepId} is blank,
   *                                                                   the run is cancelled, status
   *                                                                   is not
   *                                                                   {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_APPROVAL},
   *                                                                   or {@code runId} is blank
   */
  @Override
  public void approve(String runId, String stepId, String approverNote) {
    Validate.notBlank(stepId, "stepId must not be blank");
    WorkflowState state = loadState(runId);
    ensureNotCancelled(state, "approve");
    Validate.isTrue(state.getStatus() == WorkflowStatus.AWAITING_APPROVAL,
        "Cannot approve run '%s' in status %s".formatted(runId, state.getStatus()));

    try (RunContextManager.Scope ignored = runContextManager.open(runId, state.getWorkflowId(),
        stepId, null)) {
      String truncated = StringUtils.defaultString(approverNote)
          .substring(0, Math.min(200, StringUtils.defaultString(approverNote).length()));
      LOG.log(System.Logger.Level.INFO, "Approve requested runId={0}, stepId={1}, note={2}", runId,
          stepId, truncated);
      eventRecorder.record(runId, stepId, WorkflowEventType.APPROVED, approverNote, "user");
      state.setStatus(WorkflowStatus.RUNNING);
      state.setLastUpdatedAt(clock.instant());
      WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
      drive(state, workflow);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws com.agentforge4j.core.exception.ExecutionNotFoundException if no state exists for
   *                                                                   {@code runId}
   * @throws IllegalArgumentException                                  if {@code answers} is
   *                                                                   {@code null}, the run is
   *                                                                   cancelled, status is not
   *                                                                   {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_INPUT},
   *                                                                   a pending artifact path is
   *                                                                   inconsistent with state, or
   *                                                                   {@code runId} is blank
   */
  @Override
  public void submitInput(String runId, Map<String, String> answers) {
    Validate.notNull(answers, "answers must not be null");
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
      handleUserAnswers(runId, answers, state, pending);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws com.agentforge4j.core.exception.ExecutionNotFoundException if no state exists for
   *                                                                   {@code runId}
   * @throws IllegalArgumentException                                  if {@code runId} is blank
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
   * @throws com.agentforge4j.core.exception.ExecutionNotFoundException if no state exists for
   *                                                                   {@code runId}
   * @throws IllegalArgumentException                                  if status is
   *                                                                   {@link com.agentforge4j.core.workflow.state.WorkflowStatus#COMPLETED}
   *                                                                   or
   *                                                                   {@link com.agentforge4j.core.workflow.state.WorkflowStatus#FAILED},
   *                                                                   or {@code runId} is blank
   */
  @Override
  public void cancel(String runId) {
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
      eventRecorder.record(runId, state.getCurrentStepId(), WorkflowEventType.RUN_CANCELLED, null,
          "runtime");
    }
  }

  private static void writePromptAnswerToContext(WorkflowState state,
      Map<String, String> answers) {
    String answer = answers.values().stream()
        .findFirst()
        .orElse("");

    state.putContextValue("user.response." + state.getCurrentStepId(),
        new StringContextValue(answer)
    );
  }

  private void handleUserAnswers(String runId, Map<String, String> answers, WorkflowState state,
      ArtifactDefinition pending) {
    LOG.log(System.Logger.Level.INFO, "Submitting input runId={0}, keys={1}", runId,
        answers.keySet());
    writeAnswersToContext(state, pending, answers);
    eventRecorder.record(runId, state.getCurrentStepId(),
        WorkflowEventType.CONTEXT_UPDATED,
        "submitted input for artifact " + pending.id(), "user");

    state.setPendingArtifact(null);
    state.setStatus(WorkflowStatus.RUNNING);
    state.setLastUpdatedAt(clock.instant());

    if (state.getCurrentStepId() != null) {
      state.putStepOutput(state.getCurrentStepId(), "submitted");
    }

    WorkflowDefinition workflow = workflowRepository.get(state.getWorkflowId());
    LOG.log(System.Logger.Level.INFO,
        "After submitInput runId={0}, currentStepId={1}, status={2}, pendingArtifact={3}, stepOutputs={4}, contextKeys={5}",
        runId,
        state.getCurrentStepId(),
        state.getStatus(),
        state.getPendingArtifact() == null ? null : state.getPendingArtifact().id(),
        state.getStepOutputs().keySet(),
        state.getContext().keySet());
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
    ExecutionContext executionContext = newExecutionContext(state, workflow);
    executionContext.enterWorkflow(workflow);
    try {
      ExecutionOutcome outcome = stepSequenceExecutor.executeAll(workflow.steps(),
          executionContext);
      finaliseDrive(state, outcome);
    } catch (RuntimeException throwable) {
      failRun(state, state.getCurrentStepId(), throwable);
    } finally {
      executionContext.exitWorkflow();
      workflowStateRepository.save(state);
    }
  }

  private ExecutionContext newExecutionContext(WorkflowState state, WorkflowDefinition workflow) {
    return new ExecutionContext(state, workflow, maxNestingDepth);
  }

  private void finaliseDrive(WorkflowState state, ExecutionOutcome outcome) {
    state.setLastUpdatedAt(clock.instant());
    switch (outcome) {
      case COMPLETED, COMPLETED_SIGNAL -> {
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
    String supportId = UUID.randomUUID().toString();
    state.setStatus(WorkflowStatus.FAILED);
    state.setRunFailure(new RunFailure.ExceptionFailure(
        sanitiseFailureReason(failedStepId, throwable),
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
        "Run failed. supportId=%s, reason=%s".formatted(supportId, safeFailureReason(throwable)),
        "runtime");
  }

  private static String sanitiseFailureReason(String failedStepId, RuntimeException throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    String base = StringUtils.isBlank(message) ? "Unexpected runtime error" : message.strip();
    return StringUtils.isBlank(failedStepId) ? base
        : "Step '%s' failed: %s".formatted(failedStepId, base);
  }

  /**
   * Short failure text for workflow events — no stack trace or type names.
   */
  private static String safeFailureReason(Throwable throwable) {
    if (throwable == null) {
      return "Unexpected runtime error";
    }
    String message = throwable.getMessage();
    if (StringUtils.isBlank(message)) {
      return "Unexpected runtime error";
    }
    String stripped = message.strip();
    return stripped.substring(0, Math.min(500, stripped.length()));
  }

  private static void ensureNotCancelled(WorkflowState state, String operation) {
    Validate.isTrue(state.getStatus() != WorkflowStatus.CANCELLED,
        "Cannot %s run '%s' because it is CANCELLED"
            .formatted(operation, state.getRunId()));
  }

  private static Executable findStep(WorkflowDefinition workflow, String stepId) {
    return Validate.notNull(findInSteps(workflow.steps(), workflow, stepId),
        "Step '%s' not found in workflow '%s'".formatted(stepId, workflow.id()));
  }

  private static Executable findInSteps(List<Executable> steps,
      WorkflowDefinition enclosing,
      String stepId) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step && step.stepId().equals(stepId)) {
        return step;
      } else if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition bp = enclosing.blueprints().get(ref.blueprintId());
        return bp == null ? null : findInSteps(bp.steps(), enclosing, stepId);
      } else if (executable instanceof WorkflowDefinition nested) {
        return findInSteps(nested.steps(), nested, stepId);
      }
    }
    return null;
  }

  private static void writeAnswersToContext(WorkflowState state,
      ArtifactDefinition pending,
      Map<String, String> answers) {
    for (Map.Entry<String, String> entry : answers.entrySet()) {
      Validate.notBlank(entry.getKey(), "answer key must not be blank");
      String key = "%s.%s".formatted(pending.id(), entry.getKey());
      String value = entry.getValue();
      if (value == null) {
        state.removeContextValue(key);
        continue;
      }
      state.putContextValue(key, new StringContextValue(value));
    }
  }
}
