package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.util.Validate;

/**
 * Executes a nested {@link WorkflowDefinition}. Pushes the nested workflow onto the context's workflow stack for cycle
 * detection, delegates to {@link StepSequenceExecutor}, then pops on the way out.
 *
 * <p>This is the single choke point for nested-workflow entry (both the {@code WorkflowBehaviour}
 * handler and {@link ExecutableExecutor}'s inline-nested branch funnel through here), so the entered workflow's
 * non-deferred requirements are resolved-and-asserted here via {@link RequirementCheckpoint} — the same checkpoint the
 * runtime applies to the root workflow at run start. An unresolved required requirement throws mid-drive and fails the
 * run, mirroring the deferred first-use behaviour.
 *
 * <p>Does not create a new run — nested workflows execute under the parent run's
 * {@code runId} and share its state, mirroring how blueprints behave. If isolated nested runs are ever required, that
 * will be added explicitly, not implicitly.
 */
public final class WorkflowExecutor {

  private final RequirementResolver requirementResolver;
  private StepSequenceExecutor stepSequenceExecutor;

  public WorkflowExecutor(RequirementResolver requirementResolver) {
    this.requirementResolver = Validate.notNull(requirementResolver, "requirementResolver must not be null");
  }

  /**
   * Late-bound setter to break the construction-time cycle between this executor and {@link ExecutableExecutor}. Must
   * be called exactly once before first use.
   */
  public void setStepSequenceExecutor(StepSequenceExecutor stepSequenceExecutor) {
    Validate.isTrue(this.stepSequenceExecutor == null,
        "StepSequenceExecutor already set on WorkflowExecutor");
    this.stepSequenceExecutor = Validate.notNull(stepSequenceExecutor,
        "stepSequenceExecutor must not be null");
  }

  public ExecutionOutcome execute(WorkflowDefinition workflow, ExecutionContext executionContext) {
    Validate.notNull(workflow, "workflow must not be null");
    Validate.notNull(stepSequenceExecutor,
        "WorkflowExecutor not wired: call setStepSequenceExecutor first");
    executionContext.enterWorkflow(workflow);
    try {
      RequirementCheckpoint.assertNonDeferredResolved(workflow,
          executionContext.getState().getRunId(), requirementResolver);
      return stepSequenceExecutor.executeAll(workflow.steps(), executionContext);
    } finally {
      executionContext.exitWorkflow();
    }
  }
}
