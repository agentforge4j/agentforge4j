package com.agentforge4j.core.exception;

/**
 * Thrown when a workflow execution with the given id is not found.
 */
public class ExecutionNotFoundException extends RuntimeException {

  public ExecutionNotFoundException(String id) {
    super("Execution not found: %s".formatted(id));
  }
}
