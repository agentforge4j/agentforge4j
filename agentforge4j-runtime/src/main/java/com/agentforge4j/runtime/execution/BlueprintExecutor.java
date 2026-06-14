package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.execution.loop.LoopStrategy;
import com.agentforge4j.util.Validate;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Resolves a {@link BlueprintRef} against the current workflow's blueprints map and executes the
 * {@link BlueprintDefinition} — either as a sequential body, or under the loop strategy selected from the blueprint's
 * {@code LoopConfig}.
 *
 * <p>Resolution uses the innermost (active) workflow on {@link ExecutionContext#getActiveWorkflowStack()}.
 * When a nested workflow is in flight its blueprints shadow the parent's for the duration of that nested scope.
 *
 * <p>Loop strategies and the step-sequence executor are installed via setters
 * after construction to break the construction-time cycle with {@link ExecutableExecutor}.
 */
public final class BlueprintExecutor {

  private static final System.Logger LOG = System.getLogger(BlueprintExecutor.class.getName());

  private Map<LoopTerminationStrategy, LoopStrategy> loopStrategies;
  private StepSequenceExecutor stepSequenceExecutor;
  private TransitionGate transitionGate;

  public void setLoopStrategies(Collection<LoopStrategy> strategies) {
    Validate.isTrue(this.loopStrategies == null,
        "LoopStrategies already set on BlueprintExecutor");
    Validate.notNull(strategies, "strategies must not be null");
    this.loopStrategies = indexStrategies(strategies);
  }

  public void setStepSequenceExecutor(StepSequenceExecutor stepSequenceExecutor) {
    Validate.isTrue(this.stepSequenceExecutor == null,
        "StepSequenceExecutor already set on BlueprintExecutor");
    this.stepSequenceExecutor = Validate.notNull(stepSequenceExecutor,
        "stepSequenceExecutor must not be null");
  }

  public void setTransitionGate(TransitionGate transitionGate) {
    Validate.isTrue(this.transitionGate == null, "TransitionGate already set on BlueprintExecutor");
    this.transitionGate = Validate.notNull(transitionGate, "transitionGate must not be null");
  }

  public ExecutionOutcome execute(BlueprintRef ref, ExecutionContext executionContext) {
    Validate.notNull(ref, "ref must not be null");
    Validate.notNull(stepSequenceExecutor, "BlueprintExecutor not wired: call setStepSequenceExecutor first");
    Validate.notNull(loopStrategies, "BlueprintExecutor not wired: call setLoopStrategies first");
    Validate.notNull(transitionGate, "BlueprintExecutor not wired: call setTransitionGate first");

    Validate.isTrue(!executionContext.getActiveWorkflowStack().isEmpty(),
        "no active workflow on stack");
    WorkflowDefinition enclosing = executionContext.getActiveWorkflowStack().peek();
    BlueprintDefinition blueprint = Validate.notNull(
        enclosing.blueprints().get(ref.blueprintId()),
        "BlueprintRef '%s' cannot be resolved in workflow '%s'"
            .formatted(ref.blueprintId(), enclosing.id()));
    LOG.log(System.Logger.Level.DEBUG, "Resolving blueprint blueprintId={0}", ref.blueprintId());

    LoopConfig loopConfig = blueprint.behaviour().loopConfig();
    ExecutionOutcome outcome = resolveExecutionOutcome(executionContext, loopConfig,
        blueprint);
    if (outcome != ExecutionOutcome.COMPLETED) {
      return outcome;
    }
    if (transitionGate.suspendBlueprintIfGated(ref, blueprint.behaviour(),
        executionContext.getState())) {
      return ExecutionOutcome.PAUSED;
    }
    return ExecutionOutcome.COMPLETED;
  }

  private ExecutionOutcome resolveExecutionOutcome(ExecutionContext executionContext, LoopConfig loopConfig,
      BlueprintDefinition blueprint) {
    if (loopConfig == null) {
      return stepSequenceExecutor.executeAll(blueprint.steps(), executionContext);
    } else {
      LOG.log(System.Logger.Level.DEBUG, "Loop strategy engaged strategy={0}, maxIterations={1}",
          loopConfig.terminationStrategy(), loopConfig.maxIterations());
      return lookupStrategy(loopConfig).iterate(blueprint, loopConfig, executionContext);
    }
  }

  private LoopStrategy lookupStrategy(LoopConfig config) {
    return Validate.notNull(loopStrategies.get(config.terminationStrategy()),
        "No LoopStrategy registered for termination strategy: %s".formatted(
            config.terminationStrategy()));
  }

  private static Map<LoopTerminationStrategy, LoopStrategy> indexStrategies(
      Collection<LoopStrategy> strategies) {
    Map<LoopTerminationStrategy, LoopStrategy> byStrategy = new EnumMap<>(
        LoopTerminationStrategy.class);
    for (LoopStrategy strategy : strategies) {
      Validate.notNull(strategy, "LoopStrategy collection must not contain null entries");
      Validate.notNull(strategy.strategy(),
          "LoopStrategy '%s' must return a non-null strategy".formatted(
              strategy.getClass().getName()));
      LoopStrategy existing = byStrategy.putIfAbsent(strategy.strategy(), strategy);
      Validate.isTrue(existing == null,
          "Duplicate LoopStrategy for %s".formatted(strategy.strategy()));
    }
    return byStrategy;
  }
}
