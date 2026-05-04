package com.agentforge4j.core.exception;

/**
 * Thrown when a step with the given id is not found in the workflow definition.
 */
public class StepNotFoundException extends RuntimeException {

  public StepNotFoundException(String stepId) {
    super("Step not found: %s".formatted(stepId));
  }
}
