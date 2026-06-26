// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.WorkflowCapturePathCollector;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.WorkflowExecutor;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;

/**
 * Resolves a {@link WorkflowBehaviour}'s {@code workflowRef} against the {@link WorkflowRepository}
 * and delegates execution to the {@link WorkflowExecutor}, which pushes the nested workflow onto
 * the execution stack and guards against cycles.
 */
public final class WorkflowBehaviourHandler implements BehaviourHandler<WorkflowBehaviour> {

  private static final System.Logger LOG = System.getLogger(
      WorkflowBehaviourHandler.class.getName());

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
    // Merge the sub-workflow's reachable VALIDATE-declared paths into the run-level capture set before its
    // steps run, so a CREATE_FILE inside the sub-workflow is captured when (and only when) it validates one.
    executionContext.getState().mergeCapturedArtifactPaths(WorkflowCapturePathCollector.collect(nested));
    return workflowExecutor.execute(nested, executionContext);
  }
}
