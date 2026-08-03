// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.config.loader.validation.WorkflowValidator;
import com.agentforge4j.core.workflow.WorkflowCapturePathCollector;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowTreeWalker;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.WorkflowExecutor;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;
import java.util.Map;

/**
 * Resolves a {@link WorkflowBehaviour}'s {@code workflowRef} against the {@link WorkflowRepository}
 * and delegates execution to the {@link WorkflowExecutor}, which pushes the nested workflow onto
 * the execution stack and guards against cycles.
 */
public final class WorkflowBehaviourHandler implements BehaviourHandler<WorkflowBehaviour> {

  private static final System.Logger LOG = System.getLogger(
      WorkflowBehaviourHandler.class.getName());

  /**
   * Re-validates the resolved nested {@link WorkflowDefinition} itself, at the moment it is actually
   * about to execute — {@code WorkflowRuntimeBuilder.build()}'s own check only ever saw a snapshot of
   * the repository taken at construction time, and a dynamic or hot-reloadable
   * {@link WorkflowRepository} can return a different definition later, including one a run only
   * reaches long after {@code start()} already validated its own top-level workflow. Deeper nesting
   * is covered the same way, one level at a time, by this handler's own recursive dispatch.
   */
  private static final WorkflowValidator NESTED_WORKFLOW_VALIDATOR =
      new WorkflowValidator(WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH);

  private final WorkflowRepository workflowRepository;
  private final WorkflowExecutor workflowExecutor;

  public WorkflowBehaviourHandler(WorkflowRepository workflowRepository,
      WorkflowExecutor workflowExecutor) {
    this.workflowRepository = Validate.notNull(workflowRepository,
        "workflowRepository must not be null");
    this.workflowExecutor = Validate.notNull(workflowExecutor, "workflowExecutor must not be null");
  }

  @Override
  public Class<WorkflowBehaviour> behaviourType() {
    return WorkflowBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step,
      WorkflowBehaviour behaviour,
      ExecutionContext executionContext) {
    LOG.log(System.Logger.Level.INFO, "Workflow behaviour start stepId={0}, workflowRef={1}",
        step.stepId(), behaviour.workflowRef());
    WorkflowDefinition nested = workflowRepository.get(behaviour.workflowRef());
    // Fail before any of the nested workflow's own steps execute if it contains a COLLECTION step —
    // the definition actually retrieved here, not a stale build-time snapshot, is what is about to
    // run.
    NESTED_WORKFLOW_VALIDATOR.validateNoCollectionSteps(Map.of(nested.id(), nested));
    // Merge the sub-workflow's reachable VALIDATE-declared paths into the run-level capture set before its
    // steps run, so a CREATE_FILE inside the sub-workflow is captured when (and only when) it validates one.
    executionContext.getState().mergeCapturedArtifactPaths(WorkflowCapturePathCollector.collect(nested));
    return workflowExecutor.execute(nested, executionContext);
  }
}
