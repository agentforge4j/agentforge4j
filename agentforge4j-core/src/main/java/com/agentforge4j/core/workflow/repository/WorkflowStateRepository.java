// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.repository;

import com.agentforge4j.core.workflow.state.WorkflowState;

import java.util.List;
import java.util.Optional;

/**
 * Repository for internal runtime state.
 *
 * <p>Implementations may return live mutable WorkflowState instances.
 * Callers must treat returned state as runtime-owned and must save changes through this repository
 * after mutation.
 *
 * <p>Public API callers should use WorkflowRuntime#getState, which returns
 * a defensive snapshot.
 */
public interface WorkflowStateRepository {

  /**
   * Inserts or replaces {@code state} in the backing store.
   */
  void save(WorkflowState state);

  /**
   * Returns the latest persisted state for {@code runId}, if any.
   *
   * @param runId run id to load
   */
  Optional<WorkflowState> findById(String runId);

  /**
   * Returns all stored run states; order is implementation-defined.
   */
  List<WorkflowState> findAll();
}
