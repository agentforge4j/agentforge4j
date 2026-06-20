// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.capture;

import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link WorkflowEventLog} decorator that records every appended event in order while delegating
 * to a backing log. All runtime events flow through {@code WorkflowEventLog.append}, so this
 * captures the full ordered event stream for assertions.
 *
 * <p>The backing log is appended to <em>first</em>; the event is recorded only once that delegate
 * call returns normally. A delegate that rejects an event therefore leaves it out of
 * {@link #capturedEvents()}, so the capture reflects only events the backing log actually accepted
 * and never reports an event the runtime failed to persist.
 */
public final class CapturingWorkflowEventLog implements WorkflowEventLog {

  private final WorkflowEventLog delegate;
  private final List<WorkflowEvent> captured = new CopyOnWriteArrayList<>();

  /**
   * Decorates the given backing log.
   *
   * @param delegate the backing event log; must not be {@code null}
   */
  public CapturingWorkflowEventLog(WorkflowEventLog delegate) {
    this.delegate = Validate.notNull(delegate, "delegate must not be null");
  }

  @Override
  public void append(WorkflowEvent event) {
    delegate.append(event);
    captured.add(event);
  }

  @Override
  public List<WorkflowEvent> getEvents(String runId) {
    return delegate.getEvents(runId);
  }

  /**
   * Returns every event appended through this decorator, across all runs, in append order.
   *
   * @return an immutable snapshot of the captured events
   */
  public List<WorkflowEvent> capturedEvents() {
    return List.copyOf(captured);
  }
}
