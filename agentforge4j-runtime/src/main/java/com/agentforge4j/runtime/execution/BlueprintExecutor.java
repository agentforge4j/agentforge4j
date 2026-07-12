// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.execution.loop.LoopStrategy;
import com.agentforge4j.util.Validate;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
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
        enclosing, blueprint);
    if (outcome != ExecutionOutcome.COMPLETED) {
      return outcome;
    }
    if (transitionGate.suspendBlueprintIfGated(ref, blueprint.behaviour(),
        executionContext.getState(),
        loopBodyCompletionUid(enclosing, blueprint, executionContext.getState()))) {
      return ExecutionOutcome.PAUSED;
    }
    return ExecutionOutcome.COMPLETED;
  }

  private ExecutionOutcome resolveExecutionOutcome(ExecutionContext executionContext, LoopConfig loopConfig,
      WorkflowDefinition enclosing, BlueprintDefinition blueprint) {
    if (loopConfig == null) {
      return stepSequenceExecutor.executeAll(blueprint.steps(), executionContext);
    }
    WorkflowState state = executionContext.getState();
    String blueprintId = blueprint.blueprintId();
    // The completed-loop skip applies only to the signal-terminated strategies (AGENT_SIGNAL,
    // EVALUATOR). Those rely on the body re-emitting a termination signal each drive; on a resume
    // re-drive their body steps are skipped (already in stepOutputs) so the signal is never re-seen
    // and the loop spins to maxIterations. FIXED_COUNT and FOR_EACH self-terminate on a re-drive
    // (bounded by count / list size) and must keep running their own strategy — in particular
    // FOR_EACH must re-enter so its list-change fingerprint check still applies — so they are never
    // marked or skipped here.
    boolean signalTerminated = isSignalTerminated(loopConfig.terminationStrategy());
    if (signalTerminated && state.isLoopCompleted(blueprintId)) {
      // The loop ran to a terminal completion signal on a prior drive, so skip it on this re-drive —
      // exactly as a completed step is skipped via stepOutputs — rather than re-entering with its
      // body skipped, never re-seeing the signal, and spinning to maxIterations. A retry/rewind to
      // at or before the loop clears this marker in WorkflowState.clearEntriesFromUid (the marker's
      // completion uid falls in the cleared range), so it can never become a stale permanent skip.
      LOG.log(System.Logger.Level.DEBUG,
          "Signal loop already completed, skipping blueprintId={0}", blueprintId);
      return ExecutionOutcome.COMPLETED;
    }
    LOG.log(System.Logger.Level.DEBUG, "Loop strategy engaged strategy={0}, maxIterations={1}",
        loopConfig.terminationStrategy(), loopConfig.maxIterations());
    ExecutionOutcome outcome = lookupStrategy(loopConfig).iterate(blueprint, loopConfig,
        executionContext);
    if (signalTerminated && outcome == ExecutionOutcome.COMPLETED) {
      state.markLoopCompleted(blueprintId, loopBodyCompletionUid(enclosing, blueprint, state));
    }
    return outcome;
  }

  private static boolean isSignalTerminated(LoopTerminationStrategy strategy) {
    return strategy == LoopTerminationStrategy.AGENT_SIGNAL
        || strategy == LoopTerminationStrategy.EVALUATOR;
  }

  /**
   * Returns the highest persisted execution uid among the loop body's {@link StepDefinition}s,
   * descending into nested blueprint refs and nested workflows so a body whose steps live inside a
   * nested blueprint still yields the real completion uid rather than {@code 0} — the uid the loop is
   * considered to have completed at. Keying the completion marker on this body uid makes
   * {@link WorkflowState#clearEntriesFromUid(int, java.util.Set)} drop the marker exactly when a rewind clears the
   * body's execution range (a rewind to at or before the loop). Nested blueprint refs resolve against
   * {@code enclosing}'s blueprints map — the same resolution {@link #execute} uses. Returns
   * {@code 0} when no reachable body step has a uid (a degenerate empty body).
   */
  private static int loopBodyCompletionUid(WorkflowDefinition enclosing, BlueprintDefinition blueprint,
      WorkflowState state) {
    return maxBodyStepUid(blueprint.steps(), enclosing, state, 0);
  }

  private static int maxBodyStepUid(List<Executable> executables, WorkflowDefinition enclosing,
      WorkflowState state, int maxUid) {
    int result = maxUid;
    for (Executable executable : executables) {
      if (executable instanceof StepDefinition step) {
        // A body StepDefinition contributes its own uid, allocated by StepSequenceExecutor for
        // every step regardless of behaviour. A WORKFLOW-behaviour step is handled here too: its own
        // uid lies within the loop body's execution range, which is all the completion marker needs
        // to be cleared by a rewind to at or before the loop, so descending into the sub-workflow
        // frame (whose inner steps carry higher uids) is unnecessary.
        Integer uid = state.getStepExecutionUid().get(step.stepId());
        if (uid != null && uid > result) {
          result = uid;
        }
      } else if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition nested = enclosing.blueprints().get(ref.blueprintId());
        if (nested != null) {
          result = maxBodyStepUid(nested.steps(), enclosing, state, result);
        }
      } else if (executable instanceof WorkflowDefinition nested) {
        result = maxBodyStepUid(nested.steps(), nested, state, result);
      }
    }
    return result;
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
