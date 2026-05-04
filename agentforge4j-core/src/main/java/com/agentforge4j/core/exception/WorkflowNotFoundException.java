package com.agentforge4j.core.exception;

/**
 * Thrown when a workflow with the given id is not found in the workflow repository.
 */
public class WorkflowNotFoundException extends RuntimeException {

  public WorkflowNotFoundException(String workflowId) {
    super("Workflow not found: %s".formatted(workflowId));
  }
}
