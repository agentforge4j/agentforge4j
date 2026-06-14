package com.agentforge4j.core.workflow.requirement;

/**
 * Resolves a declared {@link WorkflowRequirement} to its opaque value for a given context.
 *
 * <p>Implementations range from the shipped {@link DefaultRequirementResolver} (default-or-empty,
 * pure {@code core}) to a richer resolver, supplied by the embedding application, that dispatches per requirement type
 * and reads persisted values. Resolution returns the opaque resolved value, or {@code null} when no value exists —
 * mirroring {@link com.agentforge4j.core.workflow.step.StepDefinition}'s String-opaque, null-when-absent conventions.
 * The runtime never invents a value: an unresolved {@code required} requirement fails fast at the run-start
 * checkpoint.
 */
public interface RequirementResolver {

  /**
   * Resolves the opaque value for the given requirement.
   *
   * @param requirement the requirement to resolve; never {@code null}
   * @param context     opaque resolution context; never {@code null}
   *
   * @return the resolved opaque value, or {@code null} when none exists
   */
  String resolve(WorkflowRequirement requirement, ResolutionContext context);
}
