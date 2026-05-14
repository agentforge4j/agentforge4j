package com.agentforge4j.runtime.event;

import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.util.Validate;
import java.time.Clock;
import java.util.UUID;

/**
 * Thin helper that builds {@link WorkflowEvent} instances and appends them to the configured
 * {@link WorkflowEventLog}.
 *
 * <p>Centralising event creation keeps the executors focused on orchestration and
 * guarantees that every event has a correctly-generated id and timestamp.
 */
public final class EventRecorder {

  private final WorkflowEventLog eventLog;
  private final Clock clock;

  /**
   * Creates a recorder.
   *
   * @param eventLog append target for constructed events
   * @param clock    source of event timestamps
   */
  public EventRecorder(WorkflowEventLog eventLog, Clock clock) {
    this.eventLog = Validate.notNull(eventLog, "eventLog must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
  }

  /**
   * Appends a new {@link WorkflowEvent} with a generated id and {@code clock} timestamp.
   *
   * @param runId     non-blank run scope id
   * @param stepId    optional step scope; may be {@code null} for run-level events
   * @param eventType non-null event discriminator
   * @param payload   optional human-readable payload; may be {@code null}
   * @param actorId   optional logical actor (for example {@code "runtime"} or {@code "user"});
   *                  forwarded as-is to the event log
   * @throws IllegalArgumentException if {@code runId} is blank or {@code eventType} is {@code null}
   */
  public void record(String runId,
      String stepId,
      WorkflowEventType eventType,
      String payload,
      String actorId) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notNull(eventType, "eventType must not be null");
    WorkflowEvent event = new WorkflowEvent(
        UUID.randomUUID().toString(),
        runId,
        stepId,
        eventType,
        payload,
        actorId,
        clock.instant());
    eventLog.append(event);
  }
}
