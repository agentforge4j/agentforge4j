package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;

public final class FailBehaviourHandler implements BehaviourHandler<FailBehaviour> {

  private static final System.Logger LOG = System.getLogger(FailBehaviourHandler.class.getName());

  @Override
  public Class<FailBehaviour> behaviourType() {
    return FailBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step,
      FailBehaviour behaviour,
      ExecutionContext executionContext) {
    LOG.log(System.Logger.Level.ERROR, "Fail behaviour reached stepId={0}, reason={1}",
        step.stepId(), behaviour.reason());
    throw new StepExecutionException(
        "Step '%s' explicitly failed: %s"
            .formatted(step.stepId(), behaviour.reason()));
  }
}
