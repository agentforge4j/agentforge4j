// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.event;

import java.util.List;

/**
 * Append-only store of {@link WorkflowEvent} records keyed by run id.
 */
public interface WorkflowEventLog {

  /**
   * Persists {@code event} for later retrieval with {@link #getEvents(String)}.
   *
   * @param event non-null event to store
   */
  void append(WorkflowEvent event);

  /**
   * Returns events for {@code runId} in a stable, typically chronological order defined by the implementation.
   *
   * @param runId run to query
   */
  List<WorkflowEvent> getEvents(String runId);
}
