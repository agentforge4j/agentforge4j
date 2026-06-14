package com.agentforge4j.core.workflow.requirement;

import com.agentforge4j.util.Validate;

/**
 * Pure-{@code core} {@link RequirementResolver}: returns a requirement's declared default and nothing else.
 *
 * <p>Never invents a value. A {@code required} requirement with no default therefore resolves to
 * {@code null} and fails fast at the run-start checkpoint — the fail-fast guarantee holds regardless of which resolver
 * is configured. A richer resolver supplied by the embedding application replaces this by being configured on
 * {@link com.agentforge4j.runtime.WorkflowRuntimeBuilder}.
 */
public final class DefaultRequirementResolver implements RequirementResolver {

  @Override
  public String resolve(WorkflowRequirement requirement, ResolutionContext context) {
    Validate.notNull(requirement, "requirement must not be null");
    return requirement.defaultJson();
  }
}
