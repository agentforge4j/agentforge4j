// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.HashMap;
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

    WorkflowState copy = original.snapshot();
    assertThat(copy).isNotSameAs(original);
    assertThat(copy.getUserPromptPauseCountForStep("s1")).isEqualTo(1);
    assertThat(copy.getLoopIterationCursor("bp-a")).isEqualTo(2);
    assertThat(copy.getForEachListFingerprint("bp-a")).contains("abc123");

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
}
