// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

/**
 * Opens a correlation scope for a drive or step so callers can attach run metadata (for example
 * MDC) without the runtime depending on a logging API.
 */
@FunctionalInterface
public interface RunContextManager {

  /** No-op scope that performs nothing on {@link Scope#close()}. */
  Scope NO_OP_SCOPE = () -> {
  };

  /** Implementation that always returns {@link #NO_OP_SCOPE}. */
  RunContextManager NO_OP = (runId, workflowId, stepId, agentId) -> NO_OP_SCOPE;

  /**
   * Opens a scope for the given identifiers. Closing the returned scope ends the scope; callers
   * should use try-with-resources.
   *
   * @param runId       current run id, or {@code null} when not yet assigned
   * @param workflowId  workflow definition id
   * @param stepId      current step id, or {@code null} when not step-scoped
   * @param agentId     current agent id, or {@code null} when not agent-scoped
   * @return scope to close when the operation finishes
   */
  Scope open(String runId, String workflowId, String stepId, String agentId);

  /** Resource scope tied to a run or step; ends correlation when closed. */
  @FunctionalInterface
  interface Scope extends AutoCloseable {

    @Override
    void close();
  }
}
