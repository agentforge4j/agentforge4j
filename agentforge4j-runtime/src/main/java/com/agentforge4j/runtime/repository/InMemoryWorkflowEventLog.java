package com.agentforge4j.runtime.repository;

import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.util.Validate;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation of {@link WorkflowEventLog}.
 *
 * <p>Events are stored per-run in insertion order. The {@code getEvents} method
 * returns an unmodifiable snapshot. The log is append-only — no update or delete is exposed.
 *
 * <p>Thread-safe for concurrent append and read.
 */
public final class InMemoryWorkflowEventLog implements WorkflowEventLog {

  private final ConcurrentMap<String, CopyOnWriteArrayList<WorkflowEvent>> eventsByRunId =
      new ConcurrentHashMap<>();

  /** Creates an empty log. */
  public InMemoryWorkflowEventLog() {
  }

  @Override
  public void append(WorkflowEvent event) {
    Validate.notNull(event, "event must not be null");
    eventsByRunId
        .computeIfAbsent(event.runId(), ignored -> new CopyOnWriteArrayList<>())
        .add(event);
  }

  @Override
  public List<WorkflowEvent> getEvents(String runId) {
    Validate.notBlank(runId, "runId must not be blank");
    CopyOnWriteArrayList<WorkflowEvent> events = eventsByRunId.get(runId);
    if (events == null) {
      return List.of();
    }
    return List.copyOf(events);
  }
}
