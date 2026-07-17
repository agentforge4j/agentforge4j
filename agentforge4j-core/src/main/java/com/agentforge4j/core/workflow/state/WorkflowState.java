// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.collection.CollectionState;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.file.ArtifactDescriptor;
import com.agentforge4j.util.Validate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
   * {@code forEachContextKey}, keyed by blueprint id. A retry/rewind crossing the loop's
   * in-progress iteration clears this together with {@link #loopIterationCursorByBlueprintId} and
   * {@link #loopIterationBodyStartUidByBlueprintId}, via {@link #clearEntriesFromUid(int, java.util.Set)} — a
   * fingerprint surviving on its own would otherwise still read as an in-progress resume.
   */
  private final Map<String, String> forEachListFingerprintByBlueprintId;
  /**
   * The execution uid at which the currently in-progress loop iteration's body began, keyed by
   * blueprint id. Cleared together with {@link #loopIterationCursorByBlueprintId} whenever a loop
   * is not in progress. Lets a loop strategy clear a just-completed iteration's step outputs,
   * execution uids, and nested completed-loop markers (via {@link #clearStepEntriesFromUid(int)})
   * before starting the next one, so {@code StepSequenceExecutor}'s resume-skip guard does not
   * mistake the previous iteration's outputs for this iteration's own — while a resume into a
   * paused iteration (same iteration number as the persisted cursor) leaves this marker untouched,
   * preserving that iteration's already-completed steps. Context values and generated-artifact
   * descriptors written by the previous iteration are deliberately not cleared at an iteration
   * boundary — see {@link #clearStepEntriesFromUid(int)}. A retry/rewind crossing this iteration's
   * start uid — whether the loop is still actively iterating or sitting paused — also clears both
   * maps together, via {@link #clearEntriesFromUid(int, java.util.Set)}.
   */
  private final Map<String, Integer> loopIterationBodyStartUidByBlueprintId;
  /**
   * Signal-terminated looped blueprints that have already run to terminal completion in this run,
   * keyed by blueprint id to the execution uid the loop body completed at (the highest
   * {@code stepExecutionUid} among its body steps). A resume re-drives the workflow from the start;
   * a completed loop here is skipped on re-entry (mirroring how a completed step is skipped via
   * {@link #stepOutputs}), so it is not re-entered and spun to {@code maxIterations}. The stored uid
   * lets {@link #clearEntriesFromUid(int, java.util.Set)} drop the marker when a retry/rewind clears the loop's
   * execution range, so a completed marker never survives a rewind to at or before the loop.
   */
  private final Map<String, Integer> completedLoopBlueprintUids;
  /**
   * For {@code AGENT_SIGNAL} (and other agent-driven termination) loops only: the execution uid at
   * which an agent step most recently applied a {@code COMPLETE} command while that blueprint's
   * iteration was on the active-loop call stack, keyed by blueprint id. Presence of a key means "the
   * last agent step evaluated for this blueprint signalled completion"; the entry is overwritten
   * (not merely set-once) on every agent step so a later, non-signalling agent step in the same
   * iteration correctly un-signals it — mirroring the runtime's transient per-drive completion
   * flag, just durable across a pause/resume where that transient flag is lost.
   *
   * <p>Cleared explicitly by {@link #clearLoopIterationCursor(String)} (a terminated or abandoned
   * loop must forget its signal, or a later run of the same blueprint id — nested or repeated —
   * would read a stale completion), and swept by uid via
   * {@link #clearStepEntriesFromUid(int)}/{@link #clearEntriesFromUid(int, java.util.Set)} exactly
   * like {@link #completedLoopBlueprintUids}, so a normal iteration-boundary advance or a legitimate
   * retry/rewind of the signalling step invalidates a stale entry the same way it invalidates other
   * loop-iteration bookkeeping.
   */
  private final Map<String, Integer> agentSignalCompletionUidByBlueprintId;
  /**
   * The blueprint id of the loop currently paused via {@code MaxIterationsAction.AWAIT_USER}, or
   * {@code null} when no loop is in that specific pause. Set only by the handler that performs that
   * pause; consumed by every resume/repositioning verb that can be called against a run in that
   * state, each of which rewinds the loop via {@link #clearEntriesFromUid(int, java.util.Set)} using
   * {@link #getLoopIterationBodyStartUid(String)} as the threshold before clearing this field, so the
   * loop genuinely restarts from iteration one instead of the resume-skip guard mistaking the
   * already-completed iteration for still in progress. A verb repositioning the run at a target that
   * would not otherwise reach this loop's own recorded range must still perform this rewind
   * unconditionally, or the loop's stale bookkeeping survives untouched. {@code PAUSED} is otherwise
   * ambiguous (an interceptor veto also leaves the run {@code PAUSED} with no loop rewind due); this
   * field is the only way to tell the two apart.
   */
  @Setter
  private String blueprintIdAwaitingMaxIterationsDecision;
  /**
   * Per-step collection-gate state, keyed by collection step id. Each value is an immutable snapshot
   * replaced wholesale by collection operations. Intentionally not uid-scoped: a closed collection is
   * never cleared by {@link #clearEntriesFromUid(int, java.util.Set)}, so a retry or rewind does not reopen it.
   */
  private final Map<String, CollectionState> collectionStateByStepId;
  /**
   * Descriptors of files emitted during the run (path, content hash, producing step, emitting step uid). At most one
   * descriptor per path (last write wins). Persisted as the durable, content-free record of generated artifacts; the
   * emitted bytes themselves live only in the transient run-scoped generated-artifact store, never here.
   */
  private final List<ArtifactDescriptor> generatedArtifactDescriptors;
  /**
   * Run-scoped union of the artifact paths declared by every reachable {@code VALIDATE} step, merged at each workflow
   * entry. Capture of {@code CREATE_FILE} bytes is gated on this set, so a run with no {@code VALIDATE} step captures
   * nothing. Repopulated idempotently each drive, so it needs no rewind handling.
   */
  private final Set<String> capturedArtifactPaths;

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
    this.loopIterationBodyStartUidByBlueprintId = new HashMap<>();
    this.completedLoopBlueprintUids = new HashMap<>();
    this.agentSignalCompletionUidByBlueprintId = new HashMap<>();
    this.collectionStateByStepId = new HashMap<>();
    this.generatedArtifactDescriptors = new ArrayList<>();
    this.capturedArtifactPaths = new LinkedHashSet<>();
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

  public Map<String, Integer> getLoopIterationBodyStartUidByBlueprintId() {
    return Collections.unmodifiableMap(loopIterationBodyStartUidByBlueprintId);
  }

  public Map<String, Integer> getCompletedLoopBlueprintUids() {
    return Collections.unmodifiableMap(completedLoopBlueprintUids);
  }

  public Map<String, Integer> getAgentSignalCompletionUidByBlueprintId() {
    return Collections.unmodifiableMap(agentSignalCompletionUidByBlueprintId);
  }

  public Map<String, CollectionState> getCollectionStateByStepId() {
    return Collections.unmodifiableMap(collectionStateByStepId);
  }

  /**
   * Returns the collection-gate state for a step, or empty when the step has no collection state.
   */
  public Optional<CollectionState> getCollectionState(String stepId) {
    return Optional.ofNullable(collectionStateByStepId.get(
        Validate.notBlank(stepId, "stepId must not be blank")));
  }

  /**
   * Stores (or replaces) the collection-gate state for its step. The state is an immutable snapshot;
   * callers replace it wholesale.
   *
   * @param state the non-null collection state to store, keyed by its own {@code stepId}
   */
  public void putCollectionState(CollectionState state) {
    Validate.notNull(state, "collection state must not be null");
    collectionStateByStepId.put(state.stepId(), state);
  }

  /**
   * Replaces all collection-gate states when loading persisted snapshot state.
   */
  public void replaceCollectionStates(Map<String, CollectionState> states) {
    collectionStateByStepId.clear();
    if (states == null) {
      return;
    }
    states.entrySet().stream()
        .filter(entry -> StringUtils.isNotBlank(entry.getKey()) && entry.getValue() != null)
        .forEach(entry -> {
          Validate.isTrue(entry.getKey().equals(entry.getValue().stepId()),
              "collectionState key '%s' does not match value stepId '%s'"
                  .formatted(entry.getKey(), entry.getValue().stepId()));
          collectionStateByStepId.put(entry.getKey(), entry.getValue());
        });
  }

  /**
   * Returns an unmodifiable view of the descriptors of files emitted during this run, in emission order (at most one per
   * path). Content-free; the emitted bytes are not persisted here.
   *
   * @return unmodifiable list of generated-artifact descriptors
   */
  public List<ArtifactDescriptor> getGeneratedArtifactDescriptors() {
    return Collections.unmodifiableList(generatedArtifactDescriptors);
  }

  /**
   * Records a descriptor for a file emitted by the current step, last-write-wins: any existing descriptor for the same
   * {@code path} is replaced, so the list holds at most one descriptor per path and the latest content hash and
   * emitting-step uid win.
   *
   * @param descriptor the descriptor to record; must not be {@code null}
   */
  public void addGeneratedArtifactDescriptor(ArtifactDescriptor descriptor) {
    Validate.notNull(descriptor, "generatedArtifactDescriptor must not be null");
    generatedArtifactDescriptors.removeIf(existing -> existing.path().equals(descriptor.path()));
    generatedArtifactDescriptors.add(descriptor);
  }

  /**
   * Returns an unmodifiable view of the run-scoped union of {@code VALIDATE}-declared artifact paths.
   *
   * @return unmodifiable set of capture-eligible artifact paths
   */
  public Set<String> getCapturedArtifactPaths() {
    return Collections.unmodifiableSet(capturedArtifactPaths);
  }

  /**
   * Merges artifact paths into the run-scoped capture set. Idempotent: re-merging the same paths on a resume or rewind
   * drive is a no-op.
   *
   * @param paths paths to add; must not be {@code null} and must contain no blank entries
   */
  public void mergeCapturedArtifactPaths(Collection<String> paths) {
    Validate.notNull(paths, "capturedArtifactPaths must not be null");
    for (String path : paths) {
      capturedArtifactPaths.add(Validate.notBlank(path, "captured artifact path must not be blank"));
    }
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

  /**
   * Clears the loop iteration cursor together with the loop's body-start-uid marker (see
   * {@link #getLoopIterationBodyStartUid(String)}) — the two are always scoped to the same
   * in-progress-or-not loop, so a loop that is no longer in progress must forget both. Also clears
   * {@link #blueprintIdAwaitingMaxIterationsDecision} when it names this blueprint: a loop that just
   * terminated (or is being rewound) can no longer be the one a pending resume rewind applies to.
   * Also clears any persisted {@link #isAgentSignalCompleted(String) agent-signal completion} for
   * this blueprint: a loop that just terminated or is being abandoned must not leave a stale signal
   * for a later, unrelated re-entry of the same blueprint id (nested or repeated) to read as already
   * complete.
   */
  public void clearLoopIterationCursor(String blueprintId) {
    String bid = Validate.notBlank(blueprintId, "blueprintId must not be blank");
    loopIterationCursorByBlueprintId.remove(bid);
    loopIterationBodyStartUidByBlueprintId.remove(bid);
    agentSignalCompletionUidByBlueprintId.remove(bid);
    if (bid.equals(blueprintIdAwaitingMaxIterationsDecision)) {
      blueprintIdAwaitingMaxIterationsDecision = null;
    }
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

  /**
   * Returns the execution uid at which the currently in-progress loop iteration's body began for
   * {@code blueprintId}, or {@code 0} if no iteration of that loop is in progress.
   */
  public int getLoopIterationBodyStartUid(String blueprintId) {
    return loopIterationBodyStartUidByBlueprintId.getOrDefault(
        Validate.notBlank(blueprintId, "blueprintId must not be blank"), 0);
  }

  /**
   * Records the execution uid at which the currently in-progress loop iteration's body began for
   * {@code blueprintId}. Overwritten each time a genuinely new iteration starts; left untouched
   * across a pause/resume of the same iteration.
   */
  public void setLoopIterationBodyStartUid(String blueprintId, int uid) {
    String bid = Validate.notBlank(blueprintId, "blueprintId must not be blank");
    Validate.isTrue(uid >= 1, "loop iteration body start uid must be at least 1");
    loopIterationBodyStartUidByBlueprintId.put(bid, uid);
  }

  /**
   * Replaces loop iteration body-start uids when loading persisted snapshot state.
   */
  public void replaceLoopIterationBodyStartUids(Map<String, Integer> bodyStartUids) {
    loopIterationBodyStartUidByBlueprintId.clear();
    if (bodyStartUids == null) {
      return;
    }
    bodyStartUids.entrySet().stream()
        .filter(entry ->
            StringUtils.isNotBlank(entry.getKey())
                && entry.getValue() != null
                && entry.getValue() >= 1)
        .forEach(entry ->
            loopIterationBodyStartUidByBlueprintId.put(entry.getKey(), entry.getValue()));
  }

  /**
   * Records that a signal-terminated looped blueprint ran to terminal completion at execution uid
   * {@code completionUid} (the highest body-step uid), so a resume re-drive skips it rather than
   * re-entering and spinning it to {@code maxIterations}. The uid lets a later rewind invalidate the
   * marker via {@link #clearEntriesFromUid(int, java.util.Set)}.
   *
   * @param blueprintId   the completed loop's blueprint id; must not be blank
   * @param completionUid the execution uid the loop body completed at; must not be negative
   */
  public void markLoopCompleted(String blueprintId, int completionUid) {
    Validate.isNotNegative(completionUid, "completionUid must not be negative");
    completedLoopBlueprintUids.put(
        Validate.notBlank(blueprintId, "blueprintId must not be blank"), completionUid);
  }

  /**
   * Returns whether a looped blueprint already ran to terminal completion in this run.
   */
  public boolean isLoopCompleted(String blueprintId) {
    return completedLoopBlueprintUids.containsKey(
        Validate.notBlank(blueprintId, "blueprintId must not be blank"));
  }

  /**
   * Replaces the completed-loop blueprint markers when loading persisted snapshot state.
   */
  public void replaceCompletedLoopBlueprintUids(Map<String, Integer> markers) {
    completedLoopBlueprintUids.clear();
    if (markers == null) {
      return;
    }
    markers.entrySet().stream()
        .filter(entry ->
            StringUtils.isNotBlank(entry.getKey())
                && entry.getValue() != null
                && entry.getValue() >= 0)
        .forEach(entry -> completedLoopBlueprintUids.put(entry.getKey(), entry.getValue()));
  }

  /**
   * Returns whether the most recently evaluated agent step for {@code blueprintId}'s active loop
   * iteration applied a {@code COMPLETE} command, persisted so the signal survives a pause/resume
   * within the same iteration.
   *
   * @param blueprintId the loop blueprint id; must not be blank
   * @return {@code true} when a completion signal is currently recorded for the blueprint
   */
  public boolean isAgentSignalCompleted(String blueprintId) {
    return agentSignalCompletionUidByBlueprintId.containsKey(
        Validate.notBlank(blueprintId, "blueprintId must not be blank"));
  }

  /**
   * Records that an agent step applied a {@code COMPLETE} command for {@code blueprintId}'s active
   * loop iteration at execution uid {@code uid}, overwriting any previous entry.
   *
   * @param blueprintId the loop blueprint id; must not be blank
   * @param uid         the signalling step's execution uid; must be at least 1
   */
  public void setAgentSignalCompleted(String blueprintId, int uid) {
    String bid = Validate.notBlank(blueprintId, "blueprintId must not be blank");
    Validate.isTrue(uid >= 1, "agent signal completion uid must be at least 1");
    agentSignalCompletionUidByBlueprintId.put(bid, uid);
  }

  /**
   * Clears {@code blueprintId}'s persisted completion signal — either because a later, non-signalling
   * agent step in the same iteration superseded it, or because the loop terminated/was abandoned.
   *
   * @param blueprintId the loop blueprint id; must not be blank
   */
  public void clearAgentSignalCompleted(String blueprintId) {
    agentSignalCompletionUidByBlueprintId.remove(
        Validate.notBlank(blueprintId, "blueprintId must not be blank"));
  }

  /**
   * Replaces agent-signal completion markers when loading persisted snapshot state.
   */
  public void replaceAgentSignalCompletionUids(Map<String, Integer> uids) {
    agentSignalCompletionUidByBlueprintId.clear();
    if (uids == null) {
      return;
    }
    uids.entrySet().stream()
        .filter(entry ->
            StringUtils.isNotBlank(entry.getKey())
                && entry.getValue() != null
                && entry.getValue() >= 1)
        .forEach(entry -> agentSignalCompletionUidByBlueprintId.put(entry.getKey(), entry.getValue()));
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
   * <p>Completed-loop markers ({@link #markLoopCompleted(String, int)}) whose completion uid is at or
   * after {@code retryUid} are also dropped: a rewind to at or before such a loop clears its body's
   * execution range, so its completion must not survive and the loop re-executes.
   *
   * <p>Generated-artifact descriptors emitted at or after {@code retryUid} are also dropped (consistent with
   * {@code stepOutputs}), so a rewind past a file-emitting step does not leave a stale descriptor for a path the
   * re-drive may not re-emit.
   *
   * <p>A loop's {@link #loopIterationCursorByBlueprintId cursor}, {@link #loopIterationBodyStartUidByBlueprintId
   * body-start-uid marker}, and (for {@code FOR_EACH} loops) {@link #forEachListFingerprintByBlueprintId list
   * fingerprint} are also dropped together when the body-start-uid marker's uid is at or after {@code retryUid}:
   * the loop's currently in-progress iteration began within the rewound range, so — whether that loop is still
   * actively iterating or sitting paused (e.g. an {@code AWAIT_USER} max-iterations pause) — it is no longer
   * meaningfully in progress once its body outputs are cleared, and must restart from iteration one on the next
   * drive rather than resume mid-way with a stale cursor. Dropping the list fingerprint too matters specifically
   * for {@code FOR_EACH}: a fingerprint surviving without its cursor would still read as an in-progress resume to
   * {@code ForEachLoopStrategy}, and a redrive that legitimately produces a different list (the common reason to
   * retry an upstream step) would then be misread as a disallowed list mutation instead of a fresh loop entry.
   *
   * <p>{@code activeBlueprintIds} excludes this exact sweep for a loop whose iteration is still genuinely in
   * progress on the caller's own call stack — for example a rewind whose threshold happens to equal that loop's
   * own body-start-uid because it targets the first step the loop's own currently-active iteration executed.
   * Such a rewind is internal to the currently-active iteration, not an external re-entry of the loop, so the
   * loop's bookkeeping must survive it; whatever owns that iteration is still on the call stack and will
   * correctly advance its own bookkeeping when it next records the start of a new iteration. Pass an empty set
   * when the caller has no loop iteration of its own active on the call stack at the point it calls this method
   * — whether because it runs before any iteration begins, or because it is deliberately abandoning or
   * restarting a loop from outside that loop's own active iteration.
   *
   * @param retryUid          the uid threshold; entries with uid &gt;= this value are cleared
   * @param activeBlueprintIds blueprint ids whose loop-cursor bookkeeping must not be swept, even if it
   *                           otherwise qualifies; must not be {@code null} (use an empty set when none apply)
   */
  public void clearEntriesFromUid(int retryUid, Set<String> activeBlueprintIds) {
    Validate.notNull(activeBlueprintIds, "activeBlueprintIds must not be null");
    clearStepEntriesFromUid(retryUid);

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

    generatedArtifactDescriptors.removeIf(descriptor -> descriptor.stepExecutionUid() >= retryUid);

    Iterator<Map.Entry<String, Integer>> loopBodyStartUidIterator =
        loopIterationBodyStartUidByBlueprintId.entrySet().iterator();
    while (loopBodyStartUidIterator.hasNext()) {
      Map.Entry<String, Integer> entry = loopBodyStartUidIterator.next();
      if (entry.getValue() >= retryUid && !activeBlueprintIds.contains(entry.getKey())) {
        loopIterationCursorByBlueprintId.remove(entry.getKey());
        forEachListFingerprintByBlueprintId.remove(entry.getKey());
        if (entry.getKey().equals(blueprintIdAwaitingMaxIterationsDecision)) {
          blueprintIdAwaitingMaxIterationsDecision = null;
        }
        loopBodyStartUidIterator.remove();
      }
    }
  }

  /**
   * Removes step outputs, step execution uids, and completed-loop markers for all steps that began
   * executing at or after {@code fromUid} — the loop-iteration-boundary subset of
   * {@link #clearEntriesFromUid(int, java.util.Set)}. Clearing the step outputs/uids is what makes
   * {@code StepSequenceExecutor}'s resume-skip guard re-execute a loop body on the next iteration;
   * dropping completed-loop markers in the range makes a nested loop re-execute on the new outer
   * iteration instead of being skipped as already complete.
   *
   * <p>Unlike a retry rewind, advancing a loop to its next iteration does not undo the previous
   * iteration: context values (and their written-at-uid bookkeeping) and generated-artifact
   * descriptors are deliberately preserved, so later iterations can read what earlier iterations
   * wrote — the cross-iteration handoff a rework/refinement loop depends on — and artifacts really
   * emitted by earlier iterations stay recorded (descriptors are upserted by path, so a re-emit on
   * a later iteration replaces rather than duplicates).
   *
   * <p>Also drops any {@link #isAgentSignalCompleted(String) agent-signal completion} marker whose
   * recorded uid is at or after {@code fromUid} — the same uid-keyed clearing as
   * {@link #completedLoopBlueprintUids}. This covers both a normal iteration-boundary advance
   * (the previous, non-terminating iteration's signal must not leak into the next one) and a
   * legitimate retry/rewind of the signalling step itself (a stale "already signalled" marker must
   * not survive a rewind that will genuinely re-execute that step).
   *
   * @param fromUid the uid threshold; step entries with uid &gt;= this value are cleared
   */
  public void clearStepEntriesFromUid(int fromUid) {
    Iterator<Map.Entry<String, Integer>> stepUidIterator = stepExecutionUid.entrySet().iterator();
    while (stepUidIterator.hasNext()) {
      Map.Entry<String, Integer> entry = stepUidIterator.next();
      if (entry.getValue() >= fromUid) {
        stepOutputs.remove(entry.getKey());
        stepUidIterator.remove();
      }
    }

    completedLoopBlueprintUids.values().removeIf(completionUid -> completionUid >= fromUid);
    agentSignalCompletionUidByBlueprintId.values().removeIf(signalUid -> signalUid >= fromUid);
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
    copy.replaceLoopIterationBodyStartUids(
        loopIterationBodyStartUidByBlueprintId.isEmpty()
            ? null
            : Map.copyOf(loopIterationBodyStartUidByBlueprintId));
    copy.replaceCompletedLoopBlueprintUids(
        completedLoopBlueprintUids.isEmpty() ? null : Map.copyOf(completedLoopBlueprintUids));
    copy.replaceAgentSignalCompletionUids(
        agentSignalCompletionUidByBlueprintId.isEmpty()
            ? null
            : Map.copyOf(agentSignalCompletionUidByBlueprintId));
    copy.setBlueprintIdAwaitingMaxIterationsDecision(blueprintIdAwaitingMaxIterationsDecision);
    copy.replaceCollectionStates(
        collectionStateByStepId.isEmpty() ? null : Map.copyOf(collectionStateByStepId));
    for (ArtifactDescriptor descriptor : generatedArtifactDescriptors) {
      copy.addGeneratedArtifactDescriptor(descriptor);
    }
    copy.mergeCapturedArtifactPaths(capturedArtifactPaths);
    return copy;
  }
}
