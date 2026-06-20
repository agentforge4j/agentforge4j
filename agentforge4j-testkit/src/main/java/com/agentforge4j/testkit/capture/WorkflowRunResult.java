// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.capture;

import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;

/**
 * The outcome of one harnessed run: the run id, a defensive snapshot of the final
 * {@link WorkflowState}, and the {@link CaptureBundle} of observed effects. This is the value the
 * assertion layer projects its verbs over.
 */
public final class WorkflowRunResult {

  private final String runId;
  private final WorkflowState finalState;
  private final CaptureBundle captures;

  /**
   * Creates a run result.
   *
   * @param runId      the run id; must not be blank
   * @param finalState a snapshot of the final state; must not be {@code null}
   * @param captures   the captured effects; must not be {@code null}
   */
  public WorkflowRunResult(String runId, WorkflowState finalState, CaptureBundle captures) {
    this.runId = Validate.notBlank(runId, "runId must not be blank");
    this.finalState = Validate.notNull(finalState, "finalState must not be null");
    this.captures = Validate.notNull(captures, "captures must not be null");
  }

  /**
   * @return the run id
   */
  public String runId() {
    return runId;
  }

  /**
   * @return a snapshot of the final run state
   */
  public WorkflowState finalState() {
    return finalState;
  }

  /**
   * @return the captured effects
   */
  public CaptureBundle captures() {
    return captures;
  }
}
