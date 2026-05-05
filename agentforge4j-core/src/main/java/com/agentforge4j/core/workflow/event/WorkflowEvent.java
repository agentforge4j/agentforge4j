package com.agentforge4j.core.workflow.event;

import com.agentforge4j.util.Validate;

import java.time.Instant;

/**
 * Immutable audit entry for a workflow run: identity, classification, optional payload, actor, and timestamp.
 *
 * @param eventId non-blank unique id for this event
 * @param runId non-blank owning run id
 * @param stepId step id if the event is step-scoped; not validated and may be blank when not applicable
 * @param eventType non-null event classification
 * @param payload optional JSON or text; not validated
 * @param actorId non-blank id of the user or agent that caused the event
 * @param occurredAt non-null event time
 * @throws IllegalArgumentException when a required component violates validation rules
 */
public record WorkflowEvent(
    String eventId,
    String runId,
    String stepId,
    WorkflowEventType eventType,
    String payload,
    String actorId,
    Instant occurredAt
) {

  public WorkflowEvent {
    Validate.notBlank(eventId, "WorkflowEvent eventId must not be blank");
    Validate.notBlank(runId, "WorkflowEvent runId must not be blank");
    Validate.notNull(eventType, "WorkflowEvent eventType must not be null");
    Validate.notBlank(actorId, "WorkflowEvent actorId must not be blank");
    Validate.notNull(occurredAt, "WorkflowEvent occurredAt must not be null");
  }
}
