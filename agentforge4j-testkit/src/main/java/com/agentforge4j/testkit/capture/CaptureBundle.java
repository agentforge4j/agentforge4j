// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.capture;

import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * The passive captures gathered during one harnessed run: the ordered event stream and the files
 * the run produced. Token usage, pending states, approvals, iteration counts, and provider/tier
 * records are projected from these by the assertion layer rather than stored separately.
 */
public final class CaptureBundle {

  private final List<WorkflowEvent> events;
  private final List<CapturedFile> files;

  /**
   * Creates a capture bundle from the captured event and file lists.
   *
   * @param events the ordered event stream; must not be {@code null}
   * @param files  the captured file writes; must not be {@code null}
   */
  public CaptureBundle(List<WorkflowEvent> events, List<CapturedFile> files) {
    this.events = List.copyOf(Validate.notNull(events, "events must not be null"));
    this.files = List.copyOf(Validate.notNull(files, "files must not be null"));
  }

  /**
   * @return the ordered event stream
   */
  public List<WorkflowEvent> events() {
    return events;
  }

  /**
   * @return the captured file writes
   */
  public List<CapturedFile> files() {
    return files;
  }
}
