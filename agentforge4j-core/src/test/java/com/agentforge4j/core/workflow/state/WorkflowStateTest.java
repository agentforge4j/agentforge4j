package com.agentforge4j.core.workflow.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

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
}
