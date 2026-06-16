// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.repository;

import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link WorkflowStateRepository} backed by a concurrent map keyed by run id.
 */
public final class InMemoryWorkflowStateRepository implements WorkflowStateRepository {

  private final ConcurrentMap<String, WorkflowState> statesByRunId = new ConcurrentHashMap<>();

  /** Creates an empty repository. */
  public InMemoryWorkflowStateRepository() {
  }

  @Override
  public void save(WorkflowState state) {
    Validate.notNull(state, "state must not be null");
    Validate.notBlank(state.getRunId(), "state.runId must not be blank");
    statesByRunId.put(state.getRunId(), state);
  }

  @Override
  public Optional<WorkflowState> findById(String runId) {
    Validate.notBlank(runId, "runId must not be blank");
    return Optional.ofNullable(statesByRunId.get(runId));
  }

  @Override
  public List<WorkflowState> findAll() {
    return List.copyOf(statesByRunId.values());
  }
}
