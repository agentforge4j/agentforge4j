package com.agentforge4j.core.workflow.repository;

import com.agentforge4j.core.workflow.WorkflowDefinition;

import java.util.Map;

/**
 * Catalog of {@link WorkflowDefinition} instances keyed by workflow id.
 */
public interface WorkflowRepository {

  /**
   * Returns the definition for {@code id}. Implementations define behaviour when no definition
   * exists for that id.
   *
   * @param id workflow id
   */
  WorkflowDefinition get(String id);

  /**
   * Returns every known definition keyed by id; may be empty.
   */
  Map<String, WorkflowDefinition> findAll();
}
