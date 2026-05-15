package com.agentforge4j.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EventRecorderTest {

  @Test
  void record_appendsEventWithClockInstant() {
    InMemoryWorkflowEventLog log = new InMemoryWorkflowEventLog();
    Instant fixed = Instant.parse("2026-05-10T15:30:00Z");
    EventRecorder recorder = new EventRecorder(log, Clock.fixed(fixed, ZoneOffset.UTC));

    recorder.record("run-1", "s1", WorkflowEventType.RUN_STARTED, "payload", "runtime");

    assertThat(log.getEvents("run-1")).hasSize(1);
    assertThat(log.getEvents("run-1").get(0).occurredAt()).isEqualTo(fixed);
    assertThat(log.getEvents("run-1").get(0).eventType()).isEqualTo(WorkflowEventType.RUN_STARTED);
    assertThat(log.getEvents("run-1").get(0).payload()).isEqualTo("payload");
    assertThat(log.getEvents("run-1").get(0).actorId()).isEqualTo("runtime");
  }

  @Test
  void record_rejectsBlankRunId() {
    EventRecorder recorder = new EventRecorder(
        new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-10T15:30:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(
        () -> recorder.record("", "s1", WorkflowEventType.RUN_STARTED, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("runId");
  }

  @Test
  void record_rejectsNullEventType() {
    EventRecorder recorder = new EventRecorder(
        new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-10T15:30:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> recorder.record("run-1", "s1", null, null, "runtime"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eventType");
  }
}
