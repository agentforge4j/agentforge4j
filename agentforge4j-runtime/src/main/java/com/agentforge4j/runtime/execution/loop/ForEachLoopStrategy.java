// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.spi.governance.WasteSignalPolicy;
import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.GeneratedArtifactStore;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.GeneratedArtifactEviction;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.util.Sha256;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Iterates the blueprint body once per element in a list stored in the shared context under {@code forEachContextKey}.
 *
 * <p>The current element is exposed under the reserved context key
 * {@link #LOOP_ITEM_KEY} for the duration of each iteration. {@code maxIterations} still applies as a ceiling — when
 * the list has more elements than the ceiling the {@link MaxIterationsHandler} is invoked.
 */
public final class ForEachLoopStrategy extends AbstractLoopStrategy {

  private static final System.Logger LOG = System.getLogger(ForEachLoopStrategy.class.getName());

  /**
   * Reserved context key that exposes the current loop element to child steps.
   */
  public static final String LOOP_ITEM_KEY = "loop.item";

  private final GeneratedArtifactStore generatedArtifactStore;

  public ForEachLoopStrategy(StepSequenceExecutor stepSequenceExecutor,
      EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler,
      ObjectMapper objectMapper,
      WasteSignalPolicy wasteSignalPolicy,
      GeneratedArtifactStore generatedArtifactStore) {
    super(stepSequenceExecutor, eventRecorder, maxIterationsHandler, objectMapper,
        wasteSignalPolicy);
    this.generatedArtifactStore = Validate.notNull(generatedArtifactStore,
        "generatedArtifactStore must not be null");
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
        restartLoop(executionContext, blueprintId);
        throw new IllegalStateException(
            "FOR_EACH list under context key '%s' changed between pause and resume for blueprint '%s' (run '%s'). Set LoopConfig.allowForEachListMutation=true on the blueprint to permit this."
                .formatted(config.forEachContextKey(), blueprintId, state.getRunId()));
      }
      LOG.log(System.Logger.Level.INFO,
          "FOR_EACH list mutated under key={0} blueprint={1}, restarting iteration",
          config.forEachContextKey(), blueprintId);
      restartLoop(executionContext, blueprintId);
      state.setForEachListFingerprint(blueprintId, currentFingerprint);
    }

    int total = items.values().size();
    int ceiling = Math.min(total, config.maxIterations());
    int start = firstLoopIterationToRun(state, blueprintId);
    if (start > ceiling) {
      restartLoop(executionContext, blueprintId);
      start = 1;
    }

    for (int iteration = start; iteration <= ceiling; iteration++) {
      LOG.log(System.Logger.Level.DEBUG,
          "Loop iteration start strategy={0}, iteration={1}, maxIterations={2}",
          strategy(), iteration, config.maxIterations());
      markLoopIterationStart(executionContext, blueprintId, iteration);
      ContextValue current = items.values().get(iteration - 1);
      Optional<ContextValue> previous = state.getContextValue(LOOP_ITEM_KEY);
      state.putContextValue(LOOP_ITEM_KEY, current);
      ExecutionOutcome outcome;
      try {
        outcome = execute(blueprint, executionContext, iteration);
      } catch (RuntimeException e) {
        restorePreviousItem(state, previous);
        // Mirrors the list-mutation/ceiling-exceeded restarts above: the thrown iteration's own
        // already-recorded step outputs and artifact bytes must be rewound too, not just the
        // cursor/fingerprint, or a later retry of a downstream step can silently skip-guard this
        // aborted iteration's steps instead of re-running them. restartLoop falls back to
        // clearLoopState when nothing was recorded yet.
        restartLoop(executionContext, blueprintId);
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
      // Wrap a scalar source value for iteration; inherit its provenance — this is internal
      // repackaging of an existing value, not a fresh external write (no re-stamp).
      return new ContextValueList(List.of(scalar), scalar.provenance());
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

  /**
   * Rewinds an abandoned in-progress iteration's body execution range (when one is recorded)
   * before forgetting the cursor and fingerprint — called both when the loop is about to restart
   * from iteration 1 in this same call (a mutated list or a cursor past the new ceiling) and when
   * the iteration is abandoned outright by a propagating exception. Without the rewind, the
   * abandoned iteration's step outputs would still satisfy {@code StepSequenceExecutor}'s
   * resume-skip guard and either the restarted loop (in-flow) or a later retry of a downstream step
   * (after the exception) would silently skip every already-recorded body step instead of
   * re-running it.
   *
   * <p>Evicts the abandoned iteration's captured artifact bytes from {@link GeneratedArtifactStore}
   * before the rewind, mirroring the other rewind chokepoints ({@code DefaultWorkflowRuntime.retry}
   * and {@code RetryPreviousBehaviourHandler}) — otherwise a per-element unique artifact path emitted
   * by the abandoned iteration would leak permanently against the run's artifact-count bound, since
   * {@link WorkflowState#clearEntriesFromUid(int, java.util.Set)} drops the descriptors but not the
   * bytes. This rewind deliberately does not exclude this loop's own blueprint id from
   * {@code clearEntriesFromUid}'s sweep — abandoning this loop's own current iteration is the whole
   * point here, unlike an internal, in-iteration rewind (for example {@code RetryPreviousBehaviourHandler}
   * retrying a step within this loop's own currently-active iteration), which must leave this loop's
   * bookkeeping alone. {@code executionContext.activeLoopBlueprintIds()} is still passed so an
   * <em>outer</em> loop whose iteration is genuinely still in progress on the call stack (this loop
   * nested inside it) is not itself wiped by this rewind.
   */
  private void restartLoop(ExecutionContext executionContext, String blueprintId) {
    WorkflowState state = executionContext.getState();
    int staleBodyStartUid = state.getLoopIterationBodyStartUid(blueprintId);
    if (staleBodyStartUid > 0) {
      GeneratedArtifactEviction.evictFromUid(generatedArtifactStore, state, staleBodyStartUid);
      // clearEntriesFromUid's own loop sweep already removes this blueprint's cursor, body-start-uid,
      // and fingerprint at this uid — clearLoopState below would only repeat that.
      state.clearEntriesFromUid(staleBodyStartUid, executionContext.activeLoopBlueprintIds());
      return;
    }
    clearLoopState(state, blueprintId);
  }

  // Package-private for direct fingerprint regression tests.
  static String fingerprint(ContextValueList list) {
    String payload = list.values().size() + "|"
        + list.values().stream().map(ForEachLoopStrategy::contentSignature)
        .collect(Collectors.joining("|"));
    return Sha256.hex(payload);
  }

  /**
   * Content-only signature of a context value, deliberately excluding {@code provenance}. The FOR_EACH fingerprint
   * tracks list <em>content</em>; using the records' {@code toString()} would couple it to provenance metadata (added
   * by the provenance work), so a re-stamped list of identical values would read as "changed" and a pre-provenance
   * persisted fingerprint would mismatch on resume.
   */
  private static String contentSignature(ContextValue value) {
    if (value instanceof StringContextValue stringValue) {
      return "S:" + stringValue.value();
    }
    if (value instanceof NumberContextValue numberValue) {
      return "N:" + numberValue.value();
    }
    if (value instanceof BooleanContextValue booleanValue) {
      return "B:" + booleanValue.value();
    }
    if (value instanceof JsonContextValue jsonValue) {
      return "J:" + jsonValue.json();
    }
    if (value instanceof ContextValueList listValue) {
      return "L:[" + listValue.values().stream().map(ForEachLoopStrategy::contentSignature)
          .collect(Collectors.joining(",")) + "]";
    }
    throw new IllegalStateException(
        "Unknown ContextValue type: " + value.getClass().getName());
  }
}
