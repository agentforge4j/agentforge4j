package com.agentforge4j.core.workflow.repository;

import com.agentforge4j.core.workflow.state.WorkflowState;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link WorkflowState} snapshots keyed by run id.
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
