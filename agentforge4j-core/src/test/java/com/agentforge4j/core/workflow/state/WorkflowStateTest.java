// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.file.ArtifactDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowStateTest {

  private static Instant t() {
    return Instant.parse("2026-03-01T00:00:00Z");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejects_blank_run_id(String runId) {
    assertThatThrownBy(() -> new WorkflowState(runId, "wf-1", null, t()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("runId");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void rejects_blank_workflow_id(String workflowId) {
    assertThatThrownBy(() -> new WorkflowState("run-1", workflowId, null, t()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("workflowId");
  }

  @Test
  void rejects_null_started_at() {
    assertThatThrownBy(() -> new WorkflowState("run-1", "wf-1", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startedAt");
  }

  @Test
  void initial_status_is_running_and_failure_accessors_are_null_without_failure() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());

    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
    assertThat(state.getFailureReason()).isNull();
    assertThat(state.getFailedStepId()).isNull();
    assertThat(state.getSupportId()).isNull();
  }

  @Test
  void failure_accessors_delegate_to_run_failure() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.setRunFailure(new RunFailure.ExceptionFailure("boom", "step-9", "support-123"));

    assertThat(state.getFailureReason()).isEqualTo("boom");
    assertThat(state.getFailedStepId()).isEqualTo("step-9");
    assertThat(state.getSupportId()).isEqualTo("support-123");
  }

  @Test
  void exposed_maps_are_unmodifiable_views() {
    var state = new WorkflowState("run-1", "wf-1", null, t());

    assertThatThrownBy(() -> state.getStepOutputs().put("s", "o"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getStepOutputs().remove("s"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getStepOutputs().clear())
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> state.getContext().put("k", new StringContextValue("v", ContextProvenance.USER_SUPPLIED)))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getContext().remove("k"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getContext().clear())
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> state.getStepExecutionUid().put("s", 1))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getStepExecutionUid().remove("s"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getStepExecutionUid().clear())
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> state.getContextKeyWrittenAtUid().put("k", 1))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getContextKeyWrittenAtUid().remove("k"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getContextKeyWrittenAtUid().clear())
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> state.getUserPromptPauseCountByStepId().put("a", 1))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getUserPromptPauseCountByStepId().remove("a"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.getUserPromptPauseCountByStepId().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejects_null_status_setter() {
    var state = new WorkflowState("run-1", "wf-1", null, t());

    assertThatThrownBy(() -> state.setStatus(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("status");
  }

  @Test
  void rejects_null_last_updated_at_setter() {
    var state = new WorkflowState("run-1", "wf-1", null, t());

    assertThatThrownBy(() -> state.setLastUpdatedAt(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lastUpdatedAt");
  }

  @Test
  void user_prompt_pause_count_increments_resets_and_replace_filters_invalid() {
    var state = new WorkflowState("run-1", "wf-1", null, t());
    assertThat(state.getUserPromptPauseCountForStep("s1")).isZero();
    state.incrementUserPromptPauseCountForStep("s1");
    state.incrementUserPromptPauseCountForStep("s1");
    assertThat(state.getUserPromptPauseCountForStep("s1")).isEqualTo(2);
    assertThat(state.getUserPromptPauseCountByStepId()).containsEntry("s1", 2);
    state.resetUserPromptPauseCountForStep("s1");
    assertThat(state.getUserPromptPauseCountForStep("s1")).isZero();

    state.incrementUserPromptPauseCountForStep("s2");
    Map<String, Integer> replacement = new HashMap<>();
    replacement.put("", 1);
    replacement.put("bad", null);
    replacement.put("x", -1);
    replacement.put("ok", 3);
    state.replaceUserPromptPauseCounts(replacement);
    assertThat(state.getUserPromptPauseCountByStepId()).containsExactly(Map.entry("ok", 3));
  }

  @Test
  void replace_user_prompt_pause_counts_null_clears_internal_map() {
    var state = new WorkflowState("run-1", "wf-1", null, t());
    state.incrementUserPromptPauseCountForStep("s1");
    state.replaceUserPromptPauseCounts(null);
    assertThat(state.getUserPromptPauseCountByStepId()).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void put_context_value_rejects_invalid_inputs(String key) {
    var state = new WorkflowState("run-1", "wf-1", null, t());
    var value = new StringContextValue("value", ContextProvenance.USER_SUPPLIED);

    assertThatThrownBy(() -> state.putContextValue(key, value))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> state.putContextValue("k", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void put_step_output_rejects_invalid_inputs(String stepId) {
    var state = new WorkflowState("run-1", "wf-1", null, t());

    assertThatThrownBy(() -> state.putStepOutput(stepId, "out"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> state.putStepOutput("step-1", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void put_step_execution_uid_rejects_blank_step_id(String stepId) {
    var state = new WorkflowState("run-1", "wf-1", null, t());

    assertThatThrownBy(() -> state.putStepExecutionUid(stepId, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void put_context_key_written_at_uid_rejects_blank_key(String key) {
    var state = new WorkflowState("run-1", "wf-1", null, t());

    assertThatThrownBy(() -> state.putContextKeyWrittenAtUid(key, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mutation_methods_update_internal_maps() {
    var state = new WorkflowState("run-1", "wf-1", null, t());
    var value = new StringContextValue("abc", ContextProvenance.USER_SUPPLIED);

    state.putContextValue("k1", value);
    state.putStepOutput("step-1", "out-1");
    state.putStepExecutionUid("step-1", 7);
    state.putContextKeyWrittenAtUid("k1", 7);

    assertThat(state.getContext()).containsEntry("k1", value);
    assertThat(state.getStepOutputs()).containsEntry("step-1", "out-1");
    assertThat(state.getStepExecutionUid()).containsEntry("step-1", 7);
    assertThat(state.getContextKeyWrittenAtUid()).containsEntry("k1", 7);
    assertThat(state.getContextValue("k1")).contains(value);
    assertThat(state.getStepOutput("step-1")).contains("out-1");
    assertThat(state.getStepExecutionUid("step-1")).contains(7);
  }

  @Test
  void clearEntriesFromUid_removes_regular_keys_at_or_after_uid() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.putContextValue("regular_key", new StringContextValue("value", ContextProvenance.USER_SUPPLIED));
    state.putContextKeyWrittenAtUid("regular_key", 5);

    state.clearEntriesFromUid(5);

    assertThat(state.getContext()).doesNotContainKey("regular_key");
    assertThat(state.getContextKeyWrittenAtUid()).doesNotContainKey("regular_key");
  }

  @Test
  void clearEntriesFromUid_retains_reserved_prefix_keys() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    StringContextValue reservedValue = new StringContextValue("reserved", ContextProvenance.USER_SUPPLIED);
    state.putContextValue("__reserved_key", reservedValue);
    state.putContextKeyWrittenAtUid("__reserved_key", 5);

    state.clearEntriesFromUid(5);

    assertThat(state.getContext()).containsEntry("__reserved_key", reservedValue);
    assertThat(state.getContextKeyWrittenAtUid()).containsEntry("__reserved_key", 5);
  }

  @Test
  void clearEntriesFromUid_retains_keys_written_before_uid() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    StringContextValue earlyValue = new StringContextValue("early", ContextProvenance.USER_SUPPLIED);
    state.putContextValue("early_key", earlyValue);
    state.putContextKeyWrittenAtUid("early_key", 3);

    state.clearEntriesFromUid(5);

    assertThat(state.getContext()).containsEntry("early_key", earlyValue);
    assertThat(state.getContextKeyWrittenAtUid()).containsEntry("early_key", 3);
  }

  @Test
  void clearEntriesFromUid_retains_llm_tokens_total() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    StringContextValue totalValue = new StringContextValue("42", ContextProvenance.USER_SUPPLIED);
    state.putContextValue(ReservedContextKeys.LLM_TOKENS_TOTAL, totalValue);
    state.putContextKeyWrittenAtUid(ReservedContextKeys.LLM_TOKENS_TOTAL, 5);

    state.clearEntriesFromUid(5);

    assertThat(ReservedContextKeys.LLM_TOKENS_TOTAL).isEqualTo("__llm_tokens_total");
    assertThat(state.getContext()).containsEntry(ReservedContextKeys.LLM_TOKENS_TOTAL, totalValue);
    assertThat(state.getContextKeyWrittenAtUid())
        .containsEntry(ReservedContextKeys.LLM_TOKENS_TOTAL, 5);
  }

  @Test
  void clearStepEntriesFromUid_clears_step_entries_and_loop_markers_but_preserves_context_and_artifacts() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.putStepExecutionUid("early-step", 3);
    state.putStepOutput("early-step", "early-out");
    state.putStepExecutionUid("body-step", 5);
    state.putStepOutput("body-step", "body-out");
    StringContextValue draft = new StringContextValue("draft-v1", ContextProvenance.LLM_GENERATED);
    state.putContextValue("draft", draft);
    state.putContextKeyWrittenAtUid("draft", 5);
    state.markLoopCompleted("nested-loop", 6);
    state.markLoopCompleted("earlier-loop", 2);
    ArtifactDescriptor emitted = new ArtifactDescriptor("report.md", "h1", "body-step", 5);
    state.addGeneratedArtifactDescriptor(emitted);

    state.clearStepEntriesFromUid(5);

    // Step outputs/uids at or after the threshold are cleared (so the resume-skip guard re-runs
    // the body), and a nested loop's completion marker in the range is dropped (so it re-executes).
    assertThat(state.getStepOutput("body-step")).isEmpty();
    assertThat(state.getStepExecutionUid("body-step")).isEmpty();
    assertThat(state.isLoopCompleted("nested-loop")).isFalse();
    assertThat(state.getStepOutput("early-step")).contains("early-out");
    assertThat(state.getStepExecutionUid("early-step")).contains(3);
    assertThat(state.isLoopCompleted("earlier-loop")).isTrue();
    // Context values (with their written-at bookkeeping) and generated-artifact descriptors are
    // preserved: an iteration boundary is not a rewind — later iterations read what earlier ones
    // wrote, and a later retry rewind can still clear the preserved keys by their recorded uid.
    assertThat(state.getContextValue("draft")).contains(draft);
    assertThat(state.getContextKeyWrittenAtUid()).containsEntry("draft", 5);
    assertThat(state.getGeneratedArtifactDescriptors()).containsExactly(emitted);
  }

  @Test
  void mutation_methods_do_not_change_last_updated_at() {
    var state = new WorkflowState("run-1", "wf-1", null, t());
    var initial = state.getLastUpdatedAt();

    state.putStepOutput("step-1", "out");
    state.putContextValue("k", new StringContextValue("v", ContextProvenance.USER_SUPPLIED));
    state.putStepExecutionUid("step-1", 1);
    state.putContextKeyWrittenAtUid("k", 1);
    state.removeContextValue("k");
    state.clearEntriesFromUid(1);

    assertThat(state.getLastUpdatedAt()).isEqualTo(initial);
  }

  @Test
  void snapshot_copies_maps_and_scalars_without_aliasing_to_original() {
    WorkflowState original = new WorkflowState("run-1", "wf-1", null, t());
    original.setStatus(WorkflowStatus.PAUSED);
    original.setCurrentStepId("s1");
    original.setLastUpdatedAt(Instant.parse("2026-03-01T01:00:00Z"));
    original.putContextValue("k", new StringContextValue("v", ContextProvenance.USER_SUPPLIED));
    original.putStepOutput("s1", "out");
    original.putStepExecutionUid("s1", 7);
    original.putContextKeyWrittenAtUid("k", 7);
    original.incrementUserPromptPauseCountForStep("s1");
    original.setLoopIterationCursor("bp-a", 2);
    original.setForEachListFingerprint("bp-a", "abc123");
    original.markLoopCompleted("bp-done", 6);
    original.setLoopIterationBodyStartUid("bp-a", 5);

    WorkflowState copy = original.snapshot();
    assertThat(copy).isNotSameAs(original);
    assertThat(copy.getUserPromptPauseCountForStep("s1")).isEqualTo(1);
    assertThat(copy.getLoopIterationCursor("bp-a")).isEqualTo(2);
    assertThat(copy.getForEachListFingerprint("bp-a")).contains("abc123");
    assertThat(copy.isLoopCompleted("bp-done")).isTrue();
    assertThat(copy.getLoopIterationBodyStartUid("bp-a")).isEqualTo(5);

    copy.setStatus(WorkflowStatus.COMPLETED);
    copy.putContextValue("extra", new StringContextValue("x", ContextProvenance.USER_SUPPLIED));
    copy.putStepOutput("s2", "more");

    assertThat(original.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(original.getContext()).doesNotContainKey("extra");
    assertThat(original.getStepOutputs()).doesNotContainKey("s2");

    assertThatThrownBy(() -> copy.getContext().put("z", new StringContextValue("nope", ContextProvenance.USER_SUPPLIED)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void snapshot_preserves_context_value_provenance() {
    WorkflowState original = new WorkflowState("run-1", "wf-1", null, t());
    original.putContextValue("trusted", new StringContextValue("a", ContextProvenance.SYSTEM_GENERATED));
    original.putContextValue("untrusted", new StringContextValue("b", ContextProvenance.USER_SUPPLIED));

    WorkflowState copy = original.snapshot();

    assertThat(copy.getContextValue("trusted").orElseThrow().provenance())
        .isEqualTo(ContextProvenance.SYSTEM_GENERATED);
    assertThat(copy.getContextValue("untrusted").orElseThrow().provenance())
        .isEqualTo(ContextProvenance.USER_SUPPLIED);
  }

  @Test
  void loop_completion_marker_records_is_checked_and_exposes_unmodifiable_view() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    assertThat(state.isLoopCompleted("bp-a")).isFalse();

    state.markLoopCompleted("bp-a", 4);

    assertThat(state.isLoopCompleted("bp-a")).isTrue();
    assertThat(state.getCompletedLoopBlueprintUids()).containsExactly(Map.entry("bp-a", 4));
    assertThatThrownBy(() -> state.markLoopCompleted(" ", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> state.markLoopCompleted("bp-b", -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> state.isLoopCompleted(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> state.getCompletedLoopBlueprintUids().put("bp-x", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void loop_iteration_body_start_uid_defaults_to_zero_and_is_validated() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    assertThat(state.getLoopIterationBodyStartUid("bp-a")).isZero();

    state.setLoopIterationBodyStartUid("bp-a", 5);
    assertThat(state.getLoopIterationBodyStartUid("bp-a")).isEqualTo(5);

    assertThatThrownBy(() -> state.setLoopIterationBodyStartUid("bp-a", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> state.setLoopIterationBodyStartUid(" ", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> state.getLoopIterationBodyStartUidByBlueprintId().put("bp-x", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void clear_loop_iteration_cursor_also_clears_the_body_start_uid_marker() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.setLoopIterationCursor("bp-a", 2);
    state.setLoopIterationBodyStartUid("bp-a", 5);

    state.clearLoopIterationCursor("bp-a");

    assertThat(state.getLoopIterationCursor("bp-a")).isZero();
    assertThat(state.getLoopIterationBodyStartUid("bp-a")).isZero();
  }

  @Test
  void replace_loop_iteration_body_start_uids_filters_invalid_and_null_clears() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.setLoopIterationBodyStartUid("bp-a", 3);

    Map<String, Integer> replacement = new HashMap<>();
    replacement.put("bp-x", 5);
    replacement.put("", 2);
    replacement.put("bp-bad", 0);
    state.replaceLoopIterationBodyStartUids(replacement);
    assertThat(state.getLoopIterationBodyStartUidByBlueprintId()).containsExactly(Map.entry("bp-x", 5));

    state.replaceLoopIterationBodyStartUids(null);
    assertThat(state.getLoopIterationBodyStartUidByBlueprintId()).isEmpty();
  }

  @Test
  void replace_completed_loop_blueprint_uids_filters_invalid_and_null_clears() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.markLoopCompleted("bp-a", 1);

    Map<String, Integer> replacement = new HashMap<>();
    replacement.put("bp-x", 5);
    replacement.put("", 2);
    replacement.put("bp-bad", -1);
    state.replaceCompletedLoopBlueprintUids(replacement);
    assertThat(state.getCompletedLoopBlueprintUids()).containsExactly(Map.entry("bp-x", 5));

    state.replaceCompletedLoopBlueprintUids(null);
    assertThat(state.getCompletedLoopBlueprintUids()).isEmpty();
  }

  @Test
  void clearEntriesFromUid_drops_loop_markers_at_or_after_uid_and_retains_earlier_ones() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.markLoopCompleted("loop-before", 3); // completed before the rewind point
    state.markLoopCompleted("loop-rewound", 8); // completed within the rewound range

    state.clearEntriesFromUid(5);

    // A rewind to uid 5 invalidates the loop that completed at/after 5, so it re-runs; the earlier
    // loop's completion survives.
    assertThat(state.isLoopCompleted("loop-rewound")).isFalse();
    assertThat(state.isLoopCompleted("loop-before")).isTrue();
  }

  @Test
  void clearEntriesFromUid_drops_stale_loop_cursor_and_body_start_uid_at_or_after_threshold() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    // A loop paused mid-iteration (e.g. an AWAIT_USER max-iterations pause) leaves its cursor and
    // body-start-uid non-zero; neither is cleared by the loop strategies themselves in that case.
    state.setLoopIterationCursor("paused-loop", 2);
    state.setLoopIterationBodyStartUid("paused-loop", 5);
    // A loop whose in-progress iteration began before the rewind point is untouched.
    state.setLoopIterationCursor("later-loop", 1);
    state.setLoopIterationBodyStartUid("later-loop", 2);

    state.clearEntriesFromUid(5);

    // A retry/rewind crossing this loop's in-progress iteration must forget the cursor too, so the
    // next drive restarts the loop from iteration 1 instead of resuming mid-way with a stale cursor.
    assertThat(state.getLoopIterationCursor("paused-loop")).isZero();
    assertThat(state.getLoopIterationBodyStartUid("paused-loop")).isZero();
    assertThat(state.getLoopIterationCursor("later-loop")).isEqualTo(1);
    assertThat(state.getLoopIterationBodyStartUid("later-loop")).isEqualTo(2);
  }

  @Test
  void clearEntriesFromUid_drops_stale_for_each_list_fingerprint_alongside_the_cursor() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    // A FOR_EACH loop's list fingerprint is a third piece of per-loop resume state, alongside the
    // cursor and body-start-uid; it must be dropped together or a stale fingerprint alone still
    // reads as an in-progress resume to ForEachLoopStrategy.
    state.setLoopIterationCursor("paused-for-each", 2);
    state.setLoopIterationBodyStartUid("paused-for-each", 5);
    state.setForEachListFingerprint("paused-for-each", "stale-fingerprint");
    state.setLoopIterationCursor("later-for-each", 1);
    state.setLoopIterationBodyStartUid("later-for-each", 2);
    state.setForEachListFingerprint("later-for-each", "kept-fingerprint");

    state.clearEntriesFromUid(5);

    assertThat(state.getForEachListFingerprint("paused-for-each")).isEmpty();
    assertThat(state.getForEachListFingerprint("later-for-each")).contains("kept-fingerprint");
  }

  @Test
  void generated_artifact_descriptors_append_in_order_and_getter_is_unmodifiable() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    ArtifactDescriptor first = new ArtifactDescriptor("agent.json", "h1", "generate", 1);
    ArtifactDescriptor second = new ArtifactDescriptor("systemprompt.md", "h2", "generate", 1);

    state.addGeneratedArtifactDescriptor(first);
    state.addGeneratedArtifactDescriptor(second);

    assertThat(state.getGeneratedArtifactDescriptors()).containsExactly(first, second);
    assertThatThrownBy(() -> state.getGeneratedArtifactDescriptors()
        .add(new ArtifactDescriptor("x", "h3", "s", 1)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void add_generated_artifact_descriptor_upserts_by_path_last_write_wins() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.addGeneratedArtifactDescriptor(new ArtifactDescriptor("agent.json", "h1", "generate", 1));
    state.addGeneratedArtifactDescriptor(new ArtifactDescriptor("agent.json", "h2", "regen", 3));

    assertThat(state.getGeneratedArtifactDescriptors())
        .containsExactly(new ArtifactDescriptor("agent.json", "h2", "regen", 3));
  }

  @Test
  void clear_entries_from_uid_drops_descriptors_at_or_after_threshold() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.addGeneratedArtifactDescriptor(new ArtifactDescriptor("kept.json", "h1", "early", 1));
    state.addGeneratedArtifactDescriptor(new ArtifactDescriptor("dropped.json", "h2", "late", 5));

    state.clearEntriesFromUid(5);

    assertThat(state.getGeneratedArtifactDescriptors())
        .containsExactly(new ArtifactDescriptor("kept.json", "h1", "early", 1));
  }

  @Test
  void add_generated_artifact_descriptor_rejects_null() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());

    assertThatThrownBy(() -> state.addGeneratedArtifactDescriptor(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("generatedArtifactDescriptor");
  }

  @Test
  void snapshot_copies_generated_artifact_descriptors_without_aliasing() {
    WorkflowState original = new WorkflowState("run-1", "wf-1", null, t());
    original.addGeneratedArtifactDescriptor(new ArtifactDescriptor("agent.json", "h1", "generate", 1));

    WorkflowState copy = original.snapshot();
    original.addGeneratedArtifactDescriptor(
        new ArtifactDescriptor("systemprompt.md", "h2", "generate", 1));

    assertThat(copy.getGeneratedArtifactDescriptors())
        .containsExactly(new ArtifactDescriptor("agent.json", "h1", "generate", 1));
  }

  @Test
  void merge_captured_artifact_paths_is_idempotent_union_and_snapshot_copies_it() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, t());
    state.mergeCapturedArtifactPaths(List.of("agent.json", "systemprompt.md"));
    state.mergeCapturedArtifactPaths(List.of("agent.json"));

    assertThat(state.getCapturedArtifactPaths())
        .containsExactlyInAnyOrder("agent.json", "systemprompt.md");

    WorkflowState copy = state.snapshot();
    assertThat(copy.getCapturedArtifactPaths())
        .containsExactlyInAnyOrder("agent.json", "systemprompt.md");
  }
}
