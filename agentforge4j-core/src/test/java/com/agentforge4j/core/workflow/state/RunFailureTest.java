// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunFailureTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

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

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void step_rejection_failure_rejects_blank_failed_step_id(String failedStepId) {
    assertThatThrownBy(() -> new RunFailure.StepRejectionFailure("reason", failedStepId, "sup"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("failedStepId");
  }

  @Test
  void step_rejection_failure_round_trips_via_kind_discriminator() throws Exception {
    RunFailure rejection = new RunFailure.StepRejectionFailure("not acceptable", "s1", "sup-1");

    String json = MAPPER.writeValueAsString(rejection);
    assertThat(json).contains("\"kind\":\"STEP_REJECTION\"");

    RunFailure parsed = MAPPER.readValue(json, RunFailure.class);
    assertThat(parsed).isInstanceOf(RunFailure.StepRejectionFailure.class).isEqualTo(rejection);
  }

  @Test
  void exception_failure_round_trips_via_kind_discriminator() throws Exception {
    RunFailure failure = new RunFailure.ExceptionFailure("boom", "s2", "sup-2");

    String json = MAPPER.writeValueAsString(failure);
    assertThat(json).contains("\"kind\":\"EXCEPTION\"");

    RunFailure parsed = MAPPER.readValue(json, RunFailure.class);
    assertThat(parsed).isInstanceOf(RunFailure.ExceptionFailure.class).isEqualTo(failure);
  }

  @Test
  void legacy_blob_without_kind_deserializes_as_exception_failure() throws Exception {
    // A snapshot persisted before the "kind" discriminator existed has no discriminator; defaultImpl
    // keeps it readable as the (only previously-possible) ExceptionFailure variant.
    String legacy = "{\"failureReason\":\"old failure\",\"failedStepId\":\"s1\","
        + "\"supportId\":\"sup-legacy\"}";

    RunFailure parsed = MAPPER.readValue(legacy, RunFailure.class);

    assertThat(parsed).isInstanceOf(RunFailure.ExceptionFailure.class);
    assertThat(parsed.failureReason()).isEqualTo("old failure");
    assertThat(parsed.failedStepId()).isEqualTo("s1");
    assertThat(parsed.supportId()).isEqualTo("sup-legacy");
  }
}
