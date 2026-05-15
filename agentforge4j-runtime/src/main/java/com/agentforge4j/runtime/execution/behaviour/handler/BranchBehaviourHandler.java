package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;
import java.util.Optional;

public final class BranchBehaviourHandler implements BehaviourHandler<BranchBehaviour> {

  private static final System.Logger LOG = System.getLogger(BranchBehaviourHandler.class.getName());

  private final EventRecorder eventRecorder;
  private ExecutableExecutor executableExecutor;

  public BranchBehaviourHandler(EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  public void setExecutableExecutor(ExecutableExecutor executableExecutor) {
    this.executableExecutor = Validate.notNull(executableExecutor,
        "executableExecutor must not be null");
  }

  @Override
  public Class<BranchBehaviour> behaviourType() {
    return BranchBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step,
      BranchBehaviour behaviour,
      ExecutionContext executionContext) {
    LOG.log(System.Logger.Level.DEBUG, "Branch behaviour start stepId={0}, contextKey={1}",
        step.stepId(), behaviour.contextKey());
    WorkflowState state = executionContext.getState();
    ContextValue contextValue = Optional.ofNullable(state.getContext().get(behaviour.contextKey()))
        .orElseThrow(() -> {
          LOG.log(System.Logger.Level.WARNING,
              "Branch context key missing stepId={0}, contextKey={1}",
              step.stepId(), behaviour.contextKey());
          return new StepExecutionException(
              "Step '%s' requires context key '%s' but it is missing"
                  .formatted(step.stepId(), behaviour.contextKey()));
        });

    String resolvedKey = resolveBranchKey(step, behaviour, contextValue);
    LOG.log(System.Logger.Level.DEBUG,
        "Branch resolved value stepId={0}, contextKey={1}, value={2}",
        step.stepId(), behaviour.contextKey(), resolvedKey);
    Executable selected = behaviour.branches().get(resolvedKey);
    String selectedBranch = resolvedKey;
    if (selected == null) {
      selected = behaviour.defaultBranch();
      selectedBranch = "default";
    }
    LOG.log(System.Logger.Level.INFO,
        "Branch selected stepId={0}, contextKey={1}, value={2}, branch={3}",
        step.stepId(), behaviour.contextKey(), resolvedKey, selectedBranch);

    String payload = "contextValue='%s', selectedBranch='%s'"
        .formatted(resolvedKey, selectedBranch);
    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.STEP_STARTED, payload,
        "runtime");
    if (selected == null) {
      LOG.log(System.Logger.Level.INFO,
          "Branch selected continuation stepId={0}, contextKey={1}, value={2}",
          step.stepId(), behaviour.contextKey(), resolvedKey);
      return ExecutionOutcome.COMPLETED;
    }
    return Validate.notNull(executableExecutor, "executableExecutor must be configured")
        .execute(selected, executionContext);
  }

  private static String resolveBranchKey(StepDefinition step,
      BranchBehaviour behaviour,
      ContextValue value) {
    if (value instanceof StringContextValue stringContextValue) {
      return stringContextValue.value();
    }
    if (value instanceof NumberContextValue numberContextValue) {
      return String.valueOf(numberContextValue.value());
    }
    if (value instanceof BooleanContextValue booleanContextValue) {
      return String.valueOf(booleanContextValue.value());
    }
    if (value instanceof JsonContextValue) {
      throw new StepExecutionException(
          "Step '%s' cannot resolve BranchBehaviour context key '%s' from JSON context value"
              .formatted(step.stepId(), behaviour.contextKey()));
    }
    if (value instanceof ContextValueList) {
      throw new StepExecutionException(
          "Step '%s' cannot resolve BranchBehaviour context key '%s' from LIST context value"
              .formatted(step.stepId(), behaviour.contextKey()));
    }
    throw new IllegalStateException(
        "Unhandled ContextValue type for branch key: " + value.getClass());
  }
}
