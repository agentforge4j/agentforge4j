package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.requirement.RequirementResolver;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;

/**
 * Dispatches an {@link Executable} to the appropriate type-specific executor.
 */
public final class ExecutableExecutor {

  private final StepExecutor stepExecutor;
  private final BlueprintExecutor blueprintExecutor;
  private final WorkflowExecutor workflowExecutor;
  private final RequirementResolver requirementResolver;

  public ExecutableExecutor(
      StepExecutor stepExecutor,
      BlueprintExecutor blueprintExecutor,
      WorkflowExecutor workflowExecutor,
      RequirementResolver requirementResolver) {
    this.stepExecutor = Validate.notNull(stepExecutor, "stepExecutor must not be null");
    this.blueprintExecutor = Validate.notNull(blueprintExecutor, "blueprintExecutor must not be null");
    this.workflowExecutor = Validate.notNull(workflowExecutor, "workflowExecutor must not be null");
    this.requirementResolver = Validate.notNull(requirementResolver, "requirementResolver must not be null");
  }

  public ExecutionOutcome execute(Executable executable, ExecutionContext executionContext) {
    Validate.notNull(executable, "executable must not be null");
    Validate.notNull(executionContext, "executionContext must not be null");
    if (executable instanceof StepDefinition step) {
      assertDeferredRequirementsResolved(step, executionContext);
      return stepExecutor.execute(step, executionContext);
    } else if (executable instanceof BlueprintRef ref) {
      return blueprintExecutor.execute(ref, executionContext);
    } else if (executable instanceof WorkflowDefinition nested) {
      return workflowExecutor.execute(nested, executionContext);
    } else if (executable instanceof BlueprintDefinition) {
      throw new IllegalArgumentException("BlueprintDefinition is not directly executable; use BlueprintRef");
    }
    throw new IllegalStateException("Unhandled Executable type: " + executable.getClass());
  }

  /**
   * Enforces the first-use contract for {@code DEFERRED} requirements that target this step, checked against the
   * workflow that actually declares them — the innermost active workflow on the context's stack (the one owning the
   * executing step), not necessarily the root. Non-deferred requirements are asserted up front when each workflow is
   * entered, so they are skipped here.
   */
  private void assertDeferredRequirementsResolved(StepDefinition step,
      ExecutionContext executionContext) {
    WorkflowDefinition declaringWorkflow = executionContext.getActiveWorkflowStack().peek();
    if (declaringWorkflow == null) {
      return;
    }
    RequirementCheckpoint.assertDeferredResolvedForStep(declaringWorkflow, step,
        executionContext.getState().getRunId(), requirementResolver);
  }
}
