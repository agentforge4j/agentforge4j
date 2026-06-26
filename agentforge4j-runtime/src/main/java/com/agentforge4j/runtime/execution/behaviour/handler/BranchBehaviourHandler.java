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
import com.agentforge4j.core.workflow.step.behaviour.BranchPredicate;
import com.agentforge4j.core.workflow.step.behaviour.BranchPredicateKind;
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
    Optional<ContextValue> contextValue = state.getContextValue(behaviour.contextKey());

    final String resolvedKey;
    final Resolution resolution;
    if (contextValue.isEmpty()) {
      // An absent context key is treated as "empty": only an EMPTY predicate may route it; otherwise
      // it remains the missing-key error (unchanged contract).
      resolvedKey = "";
      resolution = resolveAbsent(step, behaviour);
    } else {
      resolvedKey = resolveAndLogBranchKey(step, behaviour, contextValue.get());
      resolution = resolve(behaviour, resolvedKey);
    }
    LOG.log(System.Logger.Level.INFO,
        "Branch selected stepId={0}, contextKey={1}, value={2}, branch={3}",
        step.stepId(), behaviour.contextKey(), resolvedKey, resolution.selectedBranch());

    recordStepStarted(step, resolvedKey, resolution.selectedBranch(), state);
    if (!resolution.matched()) {
      if (behaviour.failOnUnmatched()) {
        throw new StepExecutionException(
            ("Step '%s' branch on context key '%s' matched no predicate, branch, or default for value '%s' and"
                + " failOnUnmatched is set")
                .formatted(step.stepId(), behaviour.contextKey(), resolvedKey));
      }
      return ExecutionOutcome.COMPLETED;
    }
    Executable selected = resolution.selected();
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

  private static Resolution resolve(BranchBehaviour behaviour, String resolvedKey) {
    // 1. Ordered predicates: first match wins. A matched predicate routes to its target (which may be
    // null — a deliberate "matched, complete" route).
    int index = 0;
    for (BranchPredicate predicate : behaviour.predicates()) {
      if (matches(predicate, resolvedKey)) {
        return new Resolution(true, predicate.target(), "predicate[%d]:%s".formatted(index, predicate.kind()));
      }
      index++;
    }
    // 2. Exact match: containsKey distinguishes an explicit match (even mapped to null —
    // "matched, complete") from an absent key, and never falls through to the default.
    if (behaviour.branches().containsKey(resolvedKey)) {
      return new Resolution(true, behaviour.branches().get(resolvedKey), resolvedKey);
    }
    // 3. Default branch when configured.
    if (behaviour.defaultBranch() != null) {
      return new Resolution(true, behaviour.defaultBranch(), "default");
    }
    // 4. Nothing routed the value: the caller fails closed when failOnUnmatched is set, else completes.
    // Labelled "default" to preserve the existing branch-decision audit semantics.
    return new Resolution(false, null, "default");
  }

  private static Resolution resolveAbsent(StepDefinition step, BranchBehaviour behaviour) {
    int index = 0;
    for (BranchPredicate predicate : behaviour.predicates()) {
      if (predicate.kind() == BranchPredicateKind.EMPTY) {
        return new Resolution(true, predicate.target(), "predicate[%d]:EMPTY".formatted(index));
      }
      index++;
    }
    throw new StepExecutionException(
        "Step '%s' requires context key '%s' but it is missing"
            .formatted(step.stepId(), behaviour.contextKey()));
  }

  private static boolean matches(BranchPredicate predicate, String resolvedKey) {
    return switch (predicate.kind()) {
      case MEMBER_OF -> predicate.members().contains(resolvedKey);
      case EMPTY -> resolvedKey == null || resolvedKey.isBlank();
    };
  }

  private record Resolution(boolean matched, Executable selected, String selectedBranch) {

  }

  private static String resolveAndLogBranchKey(StepDefinition step, BranchBehaviour behaviour,
      ContextValue contextValue) {
    String resolvedKey = resolveBranchKey(step, behaviour, contextValue);
    LOG.log(System.Logger.Level.DEBUG,
        "Branch resolved value stepId={0}, contextKey={1}, value={2}",
        step.stepId(), behaviour.contextKey(), resolvedKey);
    return resolvedKey;
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
