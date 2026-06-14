package com.agentforge4j.core.workflow.requirement;

/**
 * The target granularity of a {@link WorkflowRequirement}.
 *
 * <p>A requirement self-targets: it carries its own scope (and {@code stepId}/{@code action} where relevant). Steps
 * never reference requirements.
 */
public enum RequirementScope {

  /**
   * The requirement applies to the workflow as a whole; no {@code stepId} or {@code action}.
   */
  WORKFLOW,

  /**
   * The requirement applies to a single step; carries a {@code stepId} but no {@code action}.
   */
  STEP,

  /**
   * The requirement applies to a specific action on a single step; carries both a {@code stepId} and an {@code action}.
   * The action is an opaque string to {@code core}; its meaning belongs to the embedding application and the configured
   * {@link RequirementResolver} for the requirement type.
   */
  STEP_ACTION
}
