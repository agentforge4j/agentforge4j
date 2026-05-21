package com.agentforge4j.core.workflow.state;

import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.util.Validate;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * Mutable execution snapshot for one run: identity, status, pending gates, context maps, and
 * failure details. Field mutators are Lombok-generated except where noted on fields.
 */
@Getter
public final class WorkflowState {

  private final String runId;
  private final String workflowId;
  private final String parentRunId;
  private final Instant startedAt;

  @Setter
  private String currentStepId;
  private WorkflowStatus status;
  private Instant lastUpdatedAt;
  @Setter
  private ArtifactDefinition pendingArtifact;
  @Setter
  private String pendingUserPrompt;
  @Setter
  private RunFailure runFailure;

  private final Map<String, String> stepOutputs;
  private final Map<String, ContextValue> context;
  /**
   * Maps stepId to the monotonically increasing uid assigned when that step last began executing.
   */
  private final Map<String, Integer> stepExecutionUid;
  /**
   * Maps each context key to the uid of the step that last wrote it.
   */
  private final Map<String, Integer> contextKeyWrittenAtUid;
  /**
   * Count of blocking {@code USER_PROMPT} pauses already completed for each step id in the current
   * attempt chain (reset when the step finishes with a non-pause command outcome).
   */
  private final Map<String, Integer> userPromptPauseCountByStepId;
  /**
   * For looped blueprints only: 1-based iteration index persisted before each iteration body runs.
   * Value {@code 0} means unset when read via {@link #getLoopIterationCursor(String)}.
   */
  private final Map<String, Integer> loopIterationCursorByBlueprintId;
  /**
   * For {@code FOR_EACH} loops only: stable fingerprint of the list under
   * {@code forEachContextKey}, keyed by blueprint id.
   */
  private final Map<String, String> forEachListFingerprintByBlueprintId;

  /**
   * Creates a new run in {@link WorkflowStatus#RUNNING} with empty context and step output maps.
   *
   * @param runId       non-blank unique run id
   * @param workflowId  non-blank workflow definition id
   * @param parentRunId optional parent run when nested; may be {@code null}
   * @param startedAt   non-null start instant also used as initial {@link #lastUpdatedAt}
   * @throws IllegalArgumentException when {@code runId}, {@code workflowId}, or {@code startedAt}
   *                                  violates validation rules
   */
  public WorkflowState(
      String runId,
      String workflowId,
      String parentRunId,
      Instant startedAt) {
    this.runId = Validate.notBlank(runId, "WorkflowState runId must not be blank");
    this.workflowId = Validate.notBlank(workflowId, "WorkflowState workflowId must not be blank");
    this.parentRunId = parentRunId;
    this.startedAt = Validate.notNull(startedAt, "WorkflowState startedAt must not be null");
    this.status = WorkflowStatus.RUNNING;
    this.stepOutputs = new HashMap<>();
    this.context = new HashMap<>();
    this.stepExecutionUid = new HashMap<>();
    this.contextKeyWrittenAtUid = new HashMap<>();
    this.userPromptPauseCountByStepId = new HashMap<>();
    this.loopIterationCursorByBlueprintId = new HashMap<>();
    this.forEachListFingerprintByBlueprintId = new HashMap<>();
    this.lastUpdatedAt = startedAt;
  }

  /**
   * Returns {@link RunFailure#failureReason()} when {@code runFailure} is set; otherwise
   * {@code null}.
   */
  public String getFailureReason() {
    return runFailure == null ? null : runFailure.failureReason();
  }

  /**
   * Returns {@link RunFailure#failedStepId()} when {@code runFailure} is set; otherwise
   * {@code null}.
   */
  public String getFailedStepId() {
    return runFailure == null ? null : runFailure.failedStepId();
  }

  /**
   * Returns {@link RunFailure#supportId()} when {@code runFailure} is set; otherwise {@code null}.
   */
  public String getSupportId() {
    return runFailure == null ? null : runFailure.supportId();
  }

  public void setStatus(WorkflowStatus status) {
    this.status = Validate.notNull(status, "WorkflowState status must not be null");
  }

  public void setLastUpdatedAt(Instant lastUpdatedAt) {
    this.lastUpdatedAt =
        Validate.notNull(lastUpdatedAt, "WorkflowState lastUpdatedAt must not be null");
  }

  public Map<String, String> getStepOutputs() {
    return Collections.unmodifiableMap(stepOutputs);
  }

  public Map<String, ContextValue> getContext() {
    return Collections.unmodifiableMap(context);
  }

  public Map<String, Integer> getStepExecutionUid() {
    return Collections.unmodifiableMap(stepExecutionUid);
  }

  public Map<String, Integer> getContextKeyWrittenAtUid() {
    return Collections.unmodifiableMap(contextKeyWrittenAtUid);
  }

  public Map<String, Integer> getUserPromptPauseCountByStepId() {
    return Collections.unmodifiableMap(userPromptPauseCountByStepId);
  }

  public Map<String, Integer> getLoopIterationCursorByBlueprintId() {
    return Collections.unmodifiableMap(loopIterationCursorByBlueprintId);
  }

  public Map<String, String> getForEachListFingerprintByBlueprintId() {
    return Collections.unmodifiableMap(forEachListFingerprintByBlueprintId);
  }

  /**
   * Returns the persisted FOR_EACH list fingerprint for a blueprint, or empty when none is stored.
   */
  public Optional<String> getForEachListFingerprint(String blueprintId) {
    return Optional.ofNullable(forEachListFingerprintByBlueprintId.get(
        Validate.notBlank(blueprintId, "blueprintId must not be blank")));
  }

  public void setForEachListFingerprint(String blueprintId, String fingerprint) {
    String bid = Validate.notBlank(blueprintId, "blueprintId must not be blank");
    forEachListFingerprintByBlueprintId.put(
        bid, Validate.notBlank(fingerprint, "fingerprint must not be blank"));
  }

  public void clearForEachListFingerprint(String blueprintId) {
    forEachListFingerprintByBlueprintId.remove(
        Validate.notBlank(blueprintId, "blueprintId must not be blank"));
  }

  /**
   * Replaces FOR_EACH list fingerprints when loading persisted snapshot state.
   */
  public void replaceForEachListFingerprints(Map<String, String> fingerprints) {
    forEachListFingerprintByBlueprintId.clear();
    if (fingerprints == null) {
      return;
    }
    fingerprints.entrySet().stream()
        .filter(entry ->
            StringUtils.isNotBlank(entry.getKey()) && StringUtils.isNotBlank(entry.getValue()))
        .forEach(entry ->
            forEachListFingerprintByBlueprintId.put(entry.getKey(), entry.getValue()));
  }

  /**
   * Returns the persisted loop iteration index for a looped blueprint, or {@code 0} when none is
   * stored (start at iteration {@code 1}).
   */
  public int getLoopIterationCursor(String blueprintId) {
    return loopIterationCursorByBlueprintId.getOrDefault(
        Validate.notBlank(blueprintId, "blueprintId must not be blank"), 0);
  }

  public void setLoopIterationCursor(String blueprintId, int iteration) {
    String bid = Validate.notBlank(blueprintId, "blueprintId must not be blank");
    Validate.isTrue(iteration >= 1, "loop iteration must be at least 1");
    loopIterationCursorByBlueprintId.put(bid, iteration);
  }

  public void clearLoopIterationCursor(String blueprintId) {
    loopIterationCursorByBlueprintId.remove(
        Validate.notBlank(blueprintId, "blueprintId must not be blank"));
  }

  /**
   * Replaces loop iteration cursors when loading persisted snapshot state.
   */
  public void replaceLoopIterationCursors(Map<String, Integer> cursors) {
    loopIterationCursorByBlueprintId.clear();
    if (cursors == null) {
      return;
    }
    cursors.entrySet().stream()
        .filter(entry ->
            StringUtils.isNotBlank(entry.getKey())
                && entry.getValue() != null
                && entry.getValue() >= 1)
        .forEach(entry -> loopIterationCursorByBlueprintId.put(entry.getKey(), entry.getValue()));
  }

  public int getUserPromptPauseCountForStep(String stepId) {
    return userPromptPauseCountByStepId.getOrDefault(
        Validate.notBlank(stepId, "stepId must not be blank"), 0);
  }

  public void incrementUserPromptPauseCountForStep(String stepId) {
    String sid = Validate.notBlank(stepId, "stepId must not be blank");
    userPromptPauseCountByStepId.merge(sid, 1, Integer::sum);
  }

  public void resetUserPromptPauseCountForStep(String stepId) {
    userPromptPauseCountByStepId.remove(Validate.notBlank(stepId, "stepId must not be blank"));
  }

  /**
   * Replaces user-prompt pause counters when loading persisted snapshot state.
   */
  public void replaceUserPromptPauseCounts(Map<String, Integer> counts) {
    userPromptPauseCountByStepId.clear();
    if (counts == null) {
      return;
    }
    counts.entrySet().stream().filter(entry ->
            StringUtils.isNotBlank(entry.getKey())
                && entry.getValue() != null
                && entry.getValue() > 0)
        .forEach(entry -> userPromptPauseCountByStepId.put(entry.getKey(), entry.getValue()));
  }

  public void putContextValue(String key, ContextValue value) {
    String validatedKey = Validate.notBlank(key, "WorkflowState context key must not be blank");
    ContextValue validatedValue =
        Validate.notNull(value, "WorkflowState context value must not be null");
    context.put(validatedKey, validatedValue);
  }

  public void removeContextValue(String key) {
    String validatedKey = Validate.notBlank(key, "WorkflowState context key must not be blank");
    context.remove(validatedKey);
  }

  public void putStepOutput(String stepId, String output) {
    String validatedStepId =
        Validate.notBlank(stepId, "WorkflowState stepId must not be blank");
    String validatedOutput =
        Validate.notNull(output, "WorkflowState step output must not be null");
    stepOutputs.put(validatedStepId, validatedOutput);
  }

  public void putStepExecutionUid(String stepId, int uid) {
    String validatedStepId =
        Validate.notBlank(stepId, "WorkflowState stepId must not be blank");
    stepExecutionUid.put(validatedStepId, uid);
  }

  public void putContextKeyWrittenAtUid(String key, int uid) {
    String validatedKey =
        Validate.notBlank(key, "WorkflowState context key must not be blank");
    contextKeyWrittenAtUid.put(validatedKey, uid);
  }

  public Optional<ContextValue> getContextValue(String key) {
    String validatedKey = Validate.notBlank(key, "WorkflowState context key must not be blank");
    return Optional.ofNullable(context.get(validatedKey));
  }

  public Optional<String> getStepOutput(String stepId) {
    String validatedStepId =
        Validate.notBlank(stepId, "WorkflowState stepId must not be blank");
    return Optional.ofNullable(stepOutputs.get(validatedStepId));
  }

  public Optional<Integer> getStepExecutionUid(String stepId) {
    String validatedStepId =
        Validate.notBlank(stepId, "WorkflowState stepId must not be blank");
    return Optional.ofNullable(stepExecutionUid.get(validatedStepId));
  }

  /**
   * Removes step outputs, step execution uids, context values, and context-key-written-at-uid
   * entries for all steps that began executing at or after {@code retryUid}.
   *
   * <p>Context keys whose names start with {@code __} (the reserved runtime namespace) are
   * never removed, regardless of the uid at which they were written. This protects running counters
   * such as {@code __retry_*} attempt keys and {@code __llm_tokens_total} from being wiped by a
   * retry.
   *
   * @param retryUid the uid threshold; entries with uid &gt;= this value are cleared
   */
  public void clearEntriesFromUid(int retryUid) {
    Iterator<Map.Entry<String, Integer>> stepUidIterator = stepExecutionUid.entrySet().iterator();
    while (stepUidIterator.hasNext()) {
      Map.Entry<String, Integer> entry = stepUidIterator.next();
      if (entry.getValue() >= retryUid) {
        stepOutputs.remove(entry.getKey());
        stepUidIterator.remove();
      }
    }

    Iterator<Map.Entry<String, Integer>> contextUidIterator =
        contextKeyWrittenAtUid.entrySet().iterator();
    while (contextUidIterator.hasNext()) {
      Map.Entry<String, Integer> entry = contextUidIterator.next();
      if (entry.getValue() >= retryUid) {
        if (entry.getKey().startsWith("__")) {
          continue;
        }
        context.remove(entry.getKey());
        contextUidIterator.remove();
      }
    }
  }

  /**
   * Returns a defensive copy for handoff to external callers. The new instance uses its own map
   * instances; mutating this copy (including {@linkplain #putContextValue},
   * {@linkplain #putStepOutput}, and scalar setters) does not affect the original.
   * {@linkplain #getContext()}, {@linkplain #getStepOutputs()}, and related getters still expose
   * unmodifiable views of this copy's maps.
   *
   * <p>Context entries are copied by reference; {@link ContextValue} implementations used in
   * practice are immutable value types.
   */
  public WorkflowState snapshot() {
    WorkflowState copy =
        new WorkflowState(runId, workflowId, parentRunId, startedAt);
    copy.setCurrentStepId(currentStepId);
    copy.setStatus(status);
    copy.setLastUpdatedAt(lastUpdatedAt);
    copy.setPendingArtifact(pendingArtifact);
    copy.setPendingUserPrompt(pendingUserPrompt);
    copy.setRunFailure(runFailure);
    for (Map.Entry<String, String> entry : stepOutputs.entrySet()) {
      copy.putStepOutput(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, ContextValue> entry : context.entrySet()) {
      copy.putContextValue(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, Integer> entry : stepExecutionUid.entrySet()) {
      copy.putStepExecutionUid(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, Integer> entry : contextKeyWrittenAtUid.entrySet()) {
      copy.putContextKeyWrittenAtUid(entry.getKey(), entry.getValue());
    }
    copy.replaceUserPromptPauseCounts(
        userPromptPauseCountByStepId.isEmpty() ? null : Map.copyOf(userPromptPauseCountByStepId));
    copy.replaceLoopIterationCursors(
        loopIterationCursorByBlueprintId.isEmpty()
            ? null
            : Map.copyOf(loopIterationCursorByBlueprintId));
    copy.replaceForEachListFingerprints(
        forEachListFingerprintByBlueprintId.isEmpty()
            ? null
            : Map.copyOf(forEachListFingerprintByBlueprintId));
    return copy;
  }
}
