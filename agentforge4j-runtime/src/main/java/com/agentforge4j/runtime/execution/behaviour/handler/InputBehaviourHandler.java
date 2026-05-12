package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;
import java.time.Clock;

/**
 * Handles an {@link InputBehaviour}: looks the artifact up on the enclosing workflow, marks it
 * pending on the state, flips the status to {@code AWAITING_INPUT}, and returns
 * {@link ExecutionOutcome#PAUSED}.
 *
 * <p>When the run is resumed via {@code WorkflowRuntime.submitInput(...)} the
 * runtime writes the answers to the shared context and records the step as completed in
 * {@code stepOutputs}, after which the {@code StepSequenceExecutor} skips this step on re-drive.
 */
public final class InputBehaviourHandler implements BehaviourHandler<InputBehaviour> {

  private static final System.Logger LOG = System.getLogger(InputBehaviourHandler.class.getName());

  private final EventRecorder eventRecorder;
  private final Clock clock;

  public InputBehaviourHandler(EventRecorder eventRecorder, Clock clock) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
  }

  @Override
  public Class<InputBehaviour> behaviourType() {
    return InputBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step,
      InputBehaviour behaviour,
      ExecutionContext executionContext) {
    LOG.log(System.Logger.Level.INFO, "Workflow pausing for user input stepId={0}, artifactId={1}",
        step.stepId(), behaviour.artifactId());
    WorkflowState state = executionContext.getState();
    WorkflowDefinition enclosing = executionContext.getRootWorkflow();
    Validate.notNull(enclosing, "enclosing workflow must not be null");
    ArtifactDefinition artifact = Validate.notNull(
        enclosing.artifacts().get(behaviour.artifactId()),
        "InputBehaviour on step '%s' references unknown artifact '%s' in workflow '%s'"
            .formatted(step.stepId(), behaviour.artifactId(), enclosing.id()));

    state.setPendingArtifact(artifact);
    state.setStatus(WorkflowStatus.AWAITING_INPUT);
    state.setLastUpdatedAt(clock.instant());
    eventRecorder.record(state.getRunId(), step.stepId(),
        WorkflowEventType.AWAITING_INPUT,
        "awaiting input for artifact %s".formatted(artifact.id()), "runtime");
    return ExecutionOutcome.PAUSED;
  }
}
