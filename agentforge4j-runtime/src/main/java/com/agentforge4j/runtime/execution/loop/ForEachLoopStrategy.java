package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.util.Validate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Iterates the blueprint body once per element in a list stored in the shared context under
 * {@code forEachContextKey}.
 *
 * <p>The current element is exposed under the reserved context key
 * {@link #LOOP_ITEM_KEY} for the duration of each iteration. {@code maxIterations} still applies as
 * a ceiling — when the list has more elements than the ceiling the {@link MaxIterationsHandler} is
 * invoked.
 */
public final class ForEachLoopStrategy extends AbstractLoopStrategy {

  private static final System.Logger LOG = System.getLogger(ForEachLoopStrategy.class.getName());

  /**
   * Reserved context key that exposes the current loop element to child steps.
   */
  public static final String LOOP_ITEM_KEY = "loop.item";

  public ForEachLoopStrategy(StepSequenceExecutor stepSequenceExecutor,
      EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler) {
    super(stepSequenceExecutor, eventRecorder, maxIterationsHandler);
  }

  @Override
  public LoopTerminationStrategy strategy() {
    return LoopTerminationStrategy.FOR_EACH;
  }

  @Override
  public ExecutionOutcome iterate(BlueprintDefinition blueprint,
      LoopConfig config,
      ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    String blueprintId = blueprint.blueprintId();
    ContextValueList items = resolveItems(state, config);

    String currentFingerprint = fingerprint(items);
    String storedFingerprint = state.getForEachListFingerprint(blueprintId).orElse(null);
    int storedCursor = state.getLoopIterationCursor(blueprintId);
    boolean isResume = storedFingerprint != null || storedCursor >= 1;

    if (!isResume) {
      state.setForEachListFingerprint(blueprintId, currentFingerprint);
    } else if (storedFingerprint == null) {
      state.setForEachListFingerprint(blueprintId, currentFingerprint);
    } else if (!currentFingerprint.equals(storedFingerprint)) {
      if (!config.allowForEachListMutation()) {
        clearLoopState(state, blueprintId);
        throw new IllegalStateException(
            "FOR_EACH list under context key '%s' changed between pause and resume for blueprint '%s' (run '%s'). Set LoopConfig.allowForEachListMutation=true on the blueprint to permit this."
                .formatted(config.forEachContextKey(), blueprintId, state.getRunId()));
      }
      LOG.log(System.Logger.Level.INFO,
          "FOR_EACH list mutated under key={0} blueprint={1}, restarting iteration",
          config.forEachContextKey(), blueprintId);
      clearLoopState(state, blueprintId);
      state.setForEachListFingerprint(blueprintId, currentFingerprint);
    }

    int total = items.values().size();
    int ceiling = Math.min(total, config.maxIterations());
    int start = firstLoopIterationToRun(state, blueprintId);
    if (start > ceiling) {
      clearLoopState(state, blueprintId);
      start = 1;
    }

    for (int iteration = start; iteration <= ceiling; iteration++) {
      LOG.log(System.Logger.Level.DEBUG,
          "Loop iteration start strategy={0}, iteration={1}, maxIterations={2}",
          strategy(), iteration, config.maxIterations());
      markLoopIterationStart(state, blueprintId, iteration);
      ContextValue current = items.values().get(iteration - 1);
      Optional<ContextValue> previous = state.getContextValue(LOOP_ITEM_KEY);
      state.putContextValue(LOOP_ITEM_KEY, current);
      ExecutionOutcome outcome;
      try {
        outcome = execute(blueprint, executionContext, iteration);
      } catch (RuntimeException e) {
        restorePreviousItem(state, previous);
        clearLoopState(state, blueprintId);
        throw e;
      }
      if (outcome == ExecutionOutcome.PAUSED) {
        return outcome;
      }
      restorePreviousItem(state, previous);
      if (outcome == ExecutionOutcome.FAILED) {
        clearLoopState(state, blueprintId);
        return outcome;
      }
      if (state.getStatus() == WorkflowStatus.CANCELLED) {
        clearLoopState(state, blueprintId);
        return ExecutionOutcome.PAUSED;
      }
    }

    if (total > config.maxIterations()) {
      ExecutionOutcome outcome = maxIterationsHandler.handle(blueprint, config, executionContext);
      if (outcome == ExecutionOutcome.FAILED) {
        clearLoopState(state, blueprintId);
      }
      return outcome;
    }
    clearLoopState(state, blueprintId);
    LOG.log(System.Logger.Level.INFO,
        "Loop terminated strategy={0}, iterations={1}, reason=FOR_EACH_EXHAUSTED",
        strategy(), ceiling);
    return ExecutionOutcome.COMPLETED;
  }

  private ExecutionOutcome execute(BlueprintDefinition blueprint,
      ExecutionContext executionContext, int iteration) {
    ExecutionOutcome outcome = executeIteration(blueprint, iteration, executionContext);
    LOG.log(System.Logger.Level.DEBUG,
        "Loop iteration complete iteration={0}, terminationSignal={1}",
        iteration, false);
    return outcome;
  }

  private ContextValueList resolveItems(WorkflowState state, LoopConfig config) {
    ContextValue raw = Validate.notNull(state.getContext().get(config.forEachContextKey()),
        "FOR_EACH loop references missing context key '%s' for run '%s'"
            .formatted(config.forEachContextKey(), state.getRunId()));
    if (raw instanceof ContextValueList list) {
      return list;
    }
    if (raw instanceof StringContextValue scalar) {
      return new ContextValueList(List.of(scalar));
    }
    throw new IllegalStateException(
        "FOR_EACH loop requires a ContextValueList under key '%s' but found %s"
            .formatted(config.forEachContextKey(), raw.getClass().getSimpleName()));
  }

  private static void restorePreviousItem(WorkflowState state, Optional<ContextValue> previous) {
    if (previous.isEmpty()) {
      state.removeContextValue(LOOP_ITEM_KEY);
    } else {
      state.putContextValue(LOOP_ITEM_KEY, previous.get());
    }
  }

  private static void clearLoopState(WorkflowState state, String blueprintId) {
    clearLoopIterationCursor(state, blueprintId);
    state.clearForEachListFingerprint(blueprintId);
  }

  private static String fingerprint(ContextValueList list) {
    String payload = list.values().size() + "|"
        + list.values().stream().map(Object::toString).collect(Collectors.joining("|"));
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
