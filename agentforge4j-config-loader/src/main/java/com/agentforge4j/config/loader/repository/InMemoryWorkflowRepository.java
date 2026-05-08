package com.agentforge4j.config.loader.repository;

import com.agentforge4j.core.exception.WorkflowNotFoundException;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.util.Validate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory {@link WorkflowRepository} whose snapshot can be replaced atomically.
 */
public class InMemoryWorkflowRepository implements WorkflowRepository {

  private final AtomicReference<Map<String, WorkflowDefinition>> byIdRef;

  /**
   * Creates a repository from an initial workflow snapshot.
   *
   * @param byId initial workflows keyed by id
   */
  public InMemoryWorkflowRepository(Map<String, WorkflowDefinition> byId) {
    this.byIdRef = new AtomicReference<>(Map.copyOf(byId));
  }

  /**
   * Returns a workflow by id.
   *
   * @param id workflow id
   * @return matching workflow definition
   * @throws WorkflowNotFoundException when no workflow exists for the id
   */
  @Override
  public WorkflowDefinition get(String id) {
    Validate.notBlank(id, "Workflow id must not be blank");
    return Optional.ofNullable(byIdRef.get().get(id))
        .orElseThrow(() -> new WorkflowNotFoundException(id));
  }

  /**
   * Returns all workflows in the current snapshot.
   *
   * @return immutable map of workflow definitions keyed by id
   */
  @Override
  public Map<String, WorkflowDefinition> findAll() {
    return Map.copyOf(byIdRef.get());
  }

  /**
   * Replaces the workflow snapshot stored by this repository.
   *
   * @param byId replacement workflows keyed by id
   */
  public void replace(Map<String, WorkflowDefinition> byId) {
    byIdRef.set(Map.copyOf(byId));
  }
}
