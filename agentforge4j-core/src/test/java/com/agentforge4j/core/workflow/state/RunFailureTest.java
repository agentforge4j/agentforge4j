package com.agentforge4j.core.workflow.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunFailureTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void exception_failure_rejects_blank_failure_reason(String reason) {
    assertThatThrownBy(() -> new RunFailure.ExceptionFailure(reason, "s1", "sup"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("failureReason");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void exception_failure_rejects_blank_support_id(String supportId) {
    assertThatThrownBy(() -> new RunFailure.ExceptionFailure("reason", "s1", supportId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("supportId");
  }

  @Test
  void exception_failure_allows_null_failed_step_id() {
    RunFailure f = new RunFailure.ExceptionFailure("reason", null, "support-1");
    assertThat(f.failedStepId()).isNull();
    assertThat(f.failureReason()).isEqualTo("reason");
    assertThat(f.supportId()).isEqualTo("support-1");
  }
}
