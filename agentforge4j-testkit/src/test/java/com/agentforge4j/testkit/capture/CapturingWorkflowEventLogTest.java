// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapturingWorkflowEventLogTest {

  private static WorkflowEvent event(String id, String stepId, WorkflowEventType type) {
    return new WorkflowEvent(id, "run-1", stepId, type, null, "runtime", Instant.EPOCH);
  }

  @Test
  void capturesInOrderAndDelegates() {
    InMemoryWorkflowEventLog delegate = new InMemoryWorkflowEventLog();
    CapturingWorkflowEventLog log = new CapturingWorkflowEventLog(delegate);

    WorkflowEvent first = event("e1", "step-a", WorkflowEventType.STEP_STARTED);
    WorkflowEvent second = event("e2", "step-a", WorkflowEventType.STEP_COMPLETED);
    log.append(first);
    log.append(second);

    assertThat(log.capturedEvents()).containsExactly(first, second);
    assertThat(delegate.getEvents("run-1")).containsExactly(first, second);
  }

  @Test
  void capturedEventsSnapshotIsImmutable() {
    CapturingWorkflowEventLog log = new CapturingWorkflowEventLog(new InMemoryWorkflowEventLog());
    log.append(event("e1", "step-a", WorkflowEventType.RUN_STARTED));

    assertThat(log.capturedEvents()).hasSize(1);
    // Appending after taking a snapshot does not mutate the earlier snapshot.
    java.util.List<WorkflowEvent> snapshot = log.capturedEvents();
    log.append(event("e2", "step-a", WorkflowEventType.RUN_COMPLETED));
    assertThat(snapshot).hasSize(1);
    assertThat(log.capturedEvents()).hasSize(2);
  }

  @Test
  void doesNotCaptureWhenDelegateRejectsTheEvent() {
    WorkflowEventLog rejectingDelegate = new WorkflowEventLog() {
      @Override
      public void append(WorkflowEvent event) {
        throw new IllegalStateException("delegate rejected the event");
      }

      @Override
      public List<WorkflowEvent> getEvents(String runId) {
        return List.of();
      }
    };
    CapturingWorkflowEventLog log = new CapturingWorkflowEventLog(rejectingDelegate);

    assertThatThrownBy(() -> log.append(event("e1", "step-a", WorkflowEventType.RUN_STARTED)))
        .isInstanceOf(IllegalStateException.class);

    // The delegate failed, so the event must not appear in the capture.
    assertThat(log.capturedEvents()).isEmpty();
  }
}
