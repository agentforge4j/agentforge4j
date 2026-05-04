package com.agentforge4j.core.workflow.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEventTest {

  private static Instant t() {
    return Instant.parse("2026-01-02T12:00:00Z");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejects_blank_event_id(String eventId) {
    assertThatThrownBy(() -> new WorkflowEvent(
        eventId,
        "run-1",
        null,
        WorkflowEventType.RUN_STARTED,
        null,
        null,
        t()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eventId");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void rejects_blank_run_id(String runId) {
    assertThatThrownBy(() -> new WorkflowEvent(
        "e1",
        runId,
        null,
        WorkflowEventType.RUN_STARTED,
        null,
        null,
        t()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("runId");
  }

  @Test
  void rejects_null_event_type() {
    assertThatThrownBy(() -> new WorkflowEvent("e1", "run-1", null, null, null, null, t()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eventType");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejects_blank_actor_id(String actorId) {
    assertThatThrownBy(() -> new WorkflowEvent(
        "e1",
        "run-1",
        null,
        WorkflowEventType.RUN_STARTED,
        null,
        actorId,
        t()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actorId");
  }

  @Test
  void rejects_null_occurred_at() {
    assertThatThrownBy(() -> new WorkflowEvent(
        "e1",
        "run-1",
        null,
        WorkflowEventType.RUN_STARTED,
        null,
        "actor-1",
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("occurredAt");
  }
}
