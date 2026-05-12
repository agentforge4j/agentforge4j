package com.agentforge4j.runtime.repository;

import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryWorkflowStateRepository implements WorkflowStateRepository {

  private final ConcurrentMap<String, WorkflowState> statesByRunId = new ConcurrentHashMap<>();

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
