package com.agentforge4j.core.exception;

/**
 * Thrown when a required {@link com.agentforge4j.core.workflow.requirement.WorkflowRequirement} cannot be resolved — no
 * value is supplied by the configured {@link com.agentforge4j.core.workflow.requirement.RequirementResolver} and no
 * default is declared. Signals a configuration gap rather than a transient failure; the runtime never proceeds with an
 * unresolved required requirement, and never invents a value.
 */
public final class RequirementResolutionException extends RuntimeException {

  /**
   * Creates a new exception with the given message.
   *
   * @param message a description of the unresolved requirement
   */
  public RequirementResolutionException(String message) {
    super(message);
  }
}
