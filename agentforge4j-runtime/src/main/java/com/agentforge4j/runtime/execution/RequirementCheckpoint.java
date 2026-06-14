package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.exception.RequirementResolutionException;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.core.workflow.requirement.ResolutionContext;
import com.agentforge4j.core.workflow.requirement.ResolutionMode;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.util.Validate;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared requirement-resolution checkpoint reused at every enforcement point, so the resolve-and-assert logic exists in
 * exactly one place.
 *
 * <p>{@link #assertNonDeferredResolved} runs at the run-start checkpoint (root workflow, before any state is
 * persisted) and again whenever a nested workflow is entered, so every entered workflow's requirements are enforced —
 * not only the entry workflow's. {@link #assertDeferredResolvedForStep} enforces the first-use contract for
 * {@link ResolutionMode#DEFERRED} requirements against the workflow that actually declares them. The runtime never
 * invents a value: a {@code required} requirement that resolves to nothing fails fast.
 */
public final class RequirementCheckpoint {

  private RequirementCheckpoint() {
  }

  /**
   * Resolves and asserts every non-{@link ResolutionMode#DEFERRED} requirement declared on {@code workflow}, throwing
   * {@link RequirementResolutionException} when a {@code required} requirement resolves to nothing (no value and no
   * default). {@code DEFERRED} requirements are exempt here and checked at first use of their target step.
   *
   * @param workflow the workflow whose requirements are checked; must not be {@code null}
   * @param runId    the run id, or {@code null} when there is no run context
   * @param resolver the configured resolver; must not be {@code null}
   */
  public static void assertNonDeferredResolved(WorkflowDefinition workflow, String runId,
      RequirementResolver resolver) {
    Validate.notNull(workflow, "workflow must not be null");
    Validate.notNull(resolver, "resolver must not be null");
    if (workflow.requirements().isEmpty()) {
      return;
    }
    ResolutionContext context = new ResolutionContext(workflow.id(), runId, Map.of());
    for (WorkflowRequirement requirement : workflow.requirements()) {
      if (requirement.resolution() == ResolutionMode.DEFERRED) {
        continue;
      }
      if (isUnresolvedRequired(requirement, resolver, context)) {
        throw new RequirementResolutionException(
            "Required requirement '%s' (type '%s') for workflow '%s' is unresolved and has no default"
                .formatted(requirement.id(), requirement.type(), workflow.id()));
      }
    }
  }

  /**
   * Enforces the first-use contract for {@link ResolutionMode#DEFERRED} requirements declared on
   * {@code declaringWorkflow} that target {@code step}: a {@code required} deferred requirement still resolving to
   * nothing fails the run when the step first executes. Non-deferred requirements are asserted at workflow entry, so
   * they are skipped here.
   *
   * @param declaringWorkflow the workflow that declares the requirements (the workflow owning {@code step}); must not
   *                          be {@code null}
   * @param step              the step being executed; must not be {@code null}
   * @param runId             the run id, or {@code null} when there is no run context
   * @param resolver          the configured resolver; must not be {@code null}
   */
  public static void assertDeferredResolvedForStep(WorkflowDefinition declaringWorkflow,
      StepDefinition step, String runId, RequirementResolver resolver) {
    Validate.notNull(declaringWorkflow, "declaringWorkflow must not be null");
    Validate.notNull(step, "step must not be null");
    Validate.notNull(resolver, "resolver must not be null");
    ResolutionContext context = null;
    for (WorkflowRequirement requirement : declaringWorkflow.requirements()) {
      if (requirement.resolution() != ResolutionMode.DEFERRED) {
        continue;
      }
      if (!step.stepId().equals(requirement.stepId())) {
        continue;
      }
      if (context == null) {
        context = new ResolutionContext(declaringWorkflow.id(), runId, Map.of());
      }
      if (isUnresolvedRequired(requirement, resolver, context)) {
        throw new RequirementResolutionException(
            "Deferred requirement '%s' (type '%s') targeting step '%s' is unresolved at first use"
                .formatted(requirement.id(), requirement.type(), step.stepId()));
      }
    }
  }

  private static boolean isUnresolvedRequired(WorkflowRequirement requirement,
      RequirementResolver resolver, ResolutionContext context) {
    return requirement.required() && StringUtils.isBlank(resolver.resolve(requirement, context));
  }
}
