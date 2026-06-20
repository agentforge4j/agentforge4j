// SPDX-License-Identifier: Apache-2.0
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
    ContextValue contextValue = getContextValue(step, behaviour, state);

    String resolvedKey = resolveAndLogBranchKey(step, behaviour, contextValue);
    ResolvedSelected resolvedSelected = resolveSelected(behaviour, resolvedKey);
    LOG.log(System.Logger.Level.INFO,
        "Branch selected stepId={0}, contextKey={1}, value={2}, branch={3}",
        step.stepId(), behaviour.contextKey(), resolvedKey, resolvedSelected.selectedBranch());

    recordStepStarted(step, resolvedKey, resolvedSelected.selectedBranch(), state);
    Executable selected = resolvedSelected.selected();
    if (selected == null) {
      LOG.log(System.Logger.Level.INFO,
          "Branch selected continuation stepId={0}, contextKey={1}, value={2}",
          step.stepId(), behaviour.contextKey(), resolvedKey);
      return ExecutionOutcome.COMPLETED;
    }
    if (selected instanceof StepDefinition selectedStep) {
      if (state.getStepOutputs().containsKey(selectedStep.stepId())) {
        // A resume re-drives the workflow and re-evaluates this branch. Its selected step already
        // ran on the prior drive, so skip it rather than re-running its side effects (mirrors the
        // StepSequenceExecutor stepOutputs skip). Otherwise a selected AGENT step is re-invoked.
        LOG.log(System.Logger.Level.DEBUG,
            "Branch-selected step already completed, skipping stepId={0}", selectedStep.stepId());
        return ExecutionOutcome.COMPLETED;
      }
      // A branch-selected step is dispatched directly through the ExecutableExecutor rather than the
      // StepSequenceExecutor, so it must be assigned its execution uid here — otherwise a selected
      // AGENT step's command application fails on a null currentStepUid.
      state.putStepExecutionUid(selectedStep.stepId(),
          executionContext.allocateStepSequenceUid());
    }
    return Validate.notNull(executableExecutor, "executableExecutor must be configured")
        .execute(selected, executionContext);
  }

  private static ResolvedSelected resolveSelected(BranchBehaviour behaviour, String resolvedKey) {
    // Distinguish an explicit match (the key is present in the map, even when mapped to null) from
    // an unmatched value (the key is absent). Map.get cannot tell these apart, so a key deliberately
    // mapped to null — "matched, no sub-executable, complete" — must be detected with containsKey and
    // must never fall through to the default branch. Routing must not depend on map entry ordering.
    final Executable selected;
    final String selectedBranch;
    if (behaviour.branches().containsKey(resolvedKey)) {
      selected = behaviour.branches().get(resolvedKey);
      selectedBranch = resolvedKey;
    } else {
      // Unmatched value: use the configured default branch. A null default is the documented
      // "no fallback configured — complete and continue" contract, handled below alongside an
      // explicit null match.
      selected = behaviour.defaultBranch();
      selectedBranch = "default";
    }
    return new ResolvedSelected(selected, selectedBranch);
  }

  private record ResolvedSelected(Executable selected, String selectedBranch) {

  }

  private static String resolveAndLogBranchKey(StepDefinition step, BranchBehaviour behaviour,
      ContextValue contextValue) {
    String resolvedKey = resolveBranchKey(step, behaviour, contextValue);
    LOG.log(System.Logger.Level.DEBUG,
        "Branch resolved value stepId={0}, contextKey={1}, value={2}",
        step.stepId(), behaviour.contextKey(), resolvedKey);
    return resolvedKey;
  }

  private static ContextValue getContextValue(StepDefinition step, BranchBehaviour behaviour, WorkflowState state) {
    return Optional.ofNullable(state.getContext().get(behaviour.contextKey()))
        .orElseThrow(() -> {
          LOG.log(System.Logger.Level.WARNING,
              "Branch context key missing stepId={0}, contextKey={1}",
              step.stepId(), behaviour.contextKey());
          return new StepExecutionException(
              "Step '%s' requires context key '%s' but it is missing"
                  .formatted(step.stepId(), behaviour.contextKey()));
        });
  }

  private void recordStepStarted(StepDefinition step, String resolvedKey, String selectedBranch, WorkflowState state) {
    String payload = "contextValue='%s', selectedBranch='%s'"
        .formatted(resolvedKey, selectedBranch);
    eventRecorder.record(state.getRunId(), step.stepId(), WorkflowEventType.STEP_STARTED, payload,
        "runtime");
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
