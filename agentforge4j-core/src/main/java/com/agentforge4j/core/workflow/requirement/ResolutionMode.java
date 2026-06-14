package com.agentforge4j.core.workflow.requirement;

/**
 * When a {@link WorkflowRequirement} is expected to be resolved.
 *
 * <p>{@code core} treats every mode except {@link #DEFERRED} the same at the run-start checkpoint:
 * the value must already resolve (via the configured {@link RequirementResolver}) or the run fails fast.
 * {@code DEFERRED} alone is exempt from the up-front assertion and is checked at first use of its target step. The
 * distinction between {@link #INSTALL}, {@link #RUN_START}, and {@link #INSTALL_OR_RUN_START} is meaningful to the
 * resolving layer (the embedding application), not to the {@code core} checkpoint.
 */
public enum ResolutionMode {

  /**
   * Resolved when the workflow is installed; the resolved value is expected to be available by run start.
   */
  INSTALL,

  /**
   * Resolved at run start.
   */
  RUN_START,

  /**
   * May be resolved at install or at run start.
   */
  INSTALL_OR_RUN_START,

  /**
   * Resolution is explicitly deferred to first use of the target step; exempt from the run-start up-front assertion and
   * checked when the step first executes.
   */
  DEFERRED
}
