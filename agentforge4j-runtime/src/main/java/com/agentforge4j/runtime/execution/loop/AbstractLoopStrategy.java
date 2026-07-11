// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.spi.governance.TokenGovernanceSignal;
import com.agentforge4j.core.spi.governance.WasteSignalPolicy;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.runtime.context.CanonicalJson;
import com.agentforge4j.runtime.context.ContextFingerprint;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.waste.WasteDetector;
import com.agentforge4j.runtime.waste.WasteDetectorHistoryStore;
import com.agentforge4j.runtime.waste.WasteDetectorLoopHistory;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Shared base for loop strategies that delegate iteration bodies to {@link StepSequenceExecutor}
 * and record iteration events.
 */
abstract class AbstractLoopStrategy implements LoopStrategy {

  private static final System.Logger LOG = System.getLogger(AbstractLoopStrategy.class.getName());

  protected final StepSequenceExecutor stepSequenceExecutor;
  protected final EventRecorder eventRecorder;
  protected final MaxIterationsHandler maxIterationsHandler;
  private final ObjectMapper objectMapper;
  private final WasteSignalPolicy wasteSignalPolicy;

  protected AbstractLoopStrategy(StepSequenceExecutor stepSequenceExecutor,
      EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler,
      ObjectMapper objectMapper,
      WasteSignalPolicy wasteSignalPolicy) {
    this.stepSequenceExecutor = Validate.notNull(stepSequenceExecutor,
        "stepSequenceExecutor must not be null");
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.maxIterationsHandler = Validate.notNull(maxIterationsHandler,
        "maxIterationsHandler must not be null");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.wasteSignalPolicy = Validate.notNull(wasteSignalPolicy,
        "wasteSignalPolicy must not be null");
  }

  /**
   * First 1-based iteration index to run for a resumed loop. Stored cursor {@code 0} means start
   * at iteration {@code 1}; a positive cursor is the in-progress iteration to re-run after a
   * pause.
   */
  protected static int firstLoopIterationToRun(WorkflowState state, String blueprintId) {
    int cursor = state.getLoopIterationCursor(blueprintId);
    return Math.max(cursor, 1);
  }

  /**
   * Marks {@code iteration} as the loop iteration now starting for {@code blueprintId}, and — when
   * this is a genuinely new iteration rather than a resume into one already in progress — clears
   * the previous iteration's body step outputs, execution uids, and nested completed-loop markers
   * (via {@link WorkflowState#clearStepEntriesFromUid(int)}) so {@code StepSequenceExecutor}'s
   * resume-skip guard re-executes the body instead of mistaking the previous iteration's outputs
   * for this iteration's own. Without this clear, every loop strategy ran its body on iteration 1
   * only while still emitting {@code LOOP_ITERATION_STARTED}/{@code LOOP_ITERATION_COMPLETED} for
   * each iteration.
   *
   * <p>Context values and generated-artifact descriptors written by the previous iteration are
   * deliberately preserved: advancing a loop is not a rewind — earlier iterations really ran, so
   * their context writes stay visible to later iterations (until overwritten) and their emitted
   * artifacts stay recorded. That preserved handoff is what lets a rework/refinement loop read the
   * previous iteration's result.
   *
   * <p>The previous iteration's body range is identified by the uid at which it began
   * ({@link WorkflowState#getLoopIterationBodyStartUid(String)}), recorded the last time this
   * method genuinely started a new iteration. A resume into a paused iteration is detected by the
   * persisted cursor already equalling {@code iteration}: in that case nothing is cleared, so
   * steps completed before the pause are still skipped correctly on re-entry.
   */
  protected static void markLoopIterationStart(ExecutionContext executionContext,
      String blueprintId, int iteration) {
    WorkflowState state = executionContext.getState();
    if (state.getLoopIterationCursor(blueprintId) != iteration) {
      int previousBodyStartUid = state.getLoopIterationBodyStartUid(blueprintId);
      if (previousBodyStartUid > 0) {
        state.clearStepEntriesFromUid(previousBodyStartUid);
      }
      state.setLoopIterationBodyStartUid(blueprintId, executionContext.peekNextStepSequenceUid());
    }
    state.setLoopIterationCursor(blueprintId, iteration);
  }

  protected static void clearLoopIterationCursor(WorkflowState state, String blueprintId) {
    state.clearLoopIterationCursor(blueprintId);
  }

  /**
   * Execute a single iteration of the blueprint body, emitting start and complete loop events —
   * unless nothing in the body actually executed.
   *
   * <p>A self-terminating loop ({@code FIXED_COUNT}/{@code FOR_EACH}) that already ran to
   * completion is still re-entered on every later top-level workflow redrive (it is never marked
   * complete the way signal-terminated loops are, since {@code FOR_EACH} must keep re-checking its
   * list for mutation). On such a redrive every body step is already in {@code stepOutputs} from the
   * original pass, so {@code StepSequenceExecutor}'s resume-skip guard skips the whole body — no
   * step, nested or otherwise, allocates a new execution uid. Emitting the iteration events in that
   * case would claim an iteration happened when nothing did, the same audit-integrity defect this
   * class exists to prevent for the body itself. Comparing {@link ExecutionContext#peekNextStepSequenceUid()}
   * before and after detects this generically (it only advances when a real step executes,
   * regardless of nesting), so the events are deferred until after the body runs and only recorded
   * when at least one step genuinely executed — including a step that started and then paused, which
   * still allocates its uid before pausing.
   *
   * <p>An uncaught {@link RuntimeException} from the body is handled the same way: {@code
   * StepSequenceExecutor} allocates a step's execution uid before invoking its behaviour, so the uid
   * counter has already advanced by the time such a step throws. The counter is therefore still
   * checked (in a {@code catch}, before the exception propagates) so a genuine mid-body failure still
   * leaves a {@code LOOP_ITERATION_STARTED} audit entry — only {@code COMPLETED} is inherently
   * unreachable on this path, since the iteration never actually completed.
   *
   * <p>Brackets the body call with {@link ExecutionContext#pushActiveLoopBlueprint(String)}/
   * {@link ExecutionContext#popActiveLoopBlueprint()} so a rewind issued from inside the body — for
   * example {@code RetryPreviousBehaviourHandler} retrying this iteration's own first-executed step —
   * can tell {@code WorkflowState.clearEntriesFromUid} that this loop's iteration is still genuinely in
   * progress, not being externally re-entered, so its cursor/body-start-uid bookkeeping must survive
   * the rewind.
   */
  protected ExecutionOutcome executeIteration(BlueprintDefinition blueprint,
      int iteration,
      ExecutionContext executionContext) {
    if (executionContext.getState().getStatus() == WorkflowStatus.CANCELLED) {
      return ExecutionOutcome.PAUSED;
    }
    int uidBeforeIteration = executionContext.peekNextStepSequenceUid();
    String runId = executionContext.getState().getRunId();
    String payload = "iteration=%d".formatted(iteration);
    executionContext.pushActiveLoopBlueprint(blueprint.blueprintId());
    ExecutionOutcome outcome;
    try {
      outcome = stepSequenceExecutor.executeAll(blueprint.steps(), executionContext);
    } catch (RuntimeException exception) {
      if (executionContext.peekNextStepSequenceUid() != uidBeforeIteration) {
        eventRecorder.record(runId, blueprint.blueprintId(),
            WorkflowEventType.LOOP_ITERATION_STARTED, payload, "runtime");
      }
      throw exception;
    } finally {
      executionContext.popActiveLoopBlueprint();
    }
    if (executionContext.peekNextStepSequenceUid() == uidBeforeIteration) {
      return outcome;
    }
    eventRecorder.record(runId, blueprint.blueprintId(),
        WorkflowEventType.LOOP_ITERATION_STARTED, payload, "runtime");
    if (outcome == ExecutionOutcome.COMPLETED) {
      // Only a cleanly completed iteration is a genuine comparison point: a paused iteration will
      // re-run the same body content on resume (nothing "happened" yet to compare), and a failed
      // iteration has no stable output to fingerprint.
      evaluateWasteSignals(blueprint, iteration, executionContext.getState());
    }
    eventRecorder.record(runId, blueprint.blueprintId(),
        WorkflowEventType.LOOP_ITERATION_COMPLETED, payload, "runtime");
    return outcome;
  }

  /**
   * Evaluates {@link WasteDetector#evaluateUnchangedLoopContext} and
   * {@link WasteDetector#evaluateRepeatedLoopOutput} against this blueprint's persisted iteration
   * history (see {@link WasteDetectorHistoryStore}), records any raised signal, then persists this
   * iteration as the new history. Shared by all four {@link LoopStrategy} implementations, since
   * they all delegate here.
   *
   * <p>Both evaluator methods are documented as taking "the loop-body step" as their {@code stepId}
   * argument, but a blueprint body is a list of steps ({@link BlueprintDefinition#steps()}), not one
   * — there is no single step this method can attribute the signal to without deeper integration
   * into {@code StepSequenceExecutor}'s per-step execution (a materially larger change than this
   * wiring pass). {@code blueprint.blueprintId()} is used for both the {@code stepId} and
   * {@code blueprintId} arguments instead: for a multi-step loop body, "the loop is not making
   * progress" is fundamentally a property of the iteration as a whole, and the blueprint is this
   * runtime's natural unit of identity for that. Flagged for owner confirmation, not a silent
   * choice.
   *
   * <p>The context fingerprint covers the run's whole shared context (excluding the reserved
   * {@code __}-prefixed namespace, for the same reason {@code AgentInvoker}'s waste-signal
   * evaluation excludes it — see that class), since a loop body may write to any part of the
   * shared context, not just one selector's worth. The output fingerprint is the last
   * {@link StepDefinition} in the body's recorded {@code state.getStepOutput(...)}, normalized via
   * {@link WasteDetector#normalizeOutput} — the body's final step is this runtime's closest
   * equivalent to "the iteration's answer"; an iteration whose last step never wrote an output is
   * skipped for {@code REPEATED_LOOP_OUTPUT} only (context comparison is unaffected).
   */
  private void evaluateWasteSignals(BlueprintDefinition blueprint, int iteration,
      WorkflowState state) {
    String blueprintId = blueprint.blueprintId();
    String contextFingerprint = ContextFingerprint.of(canonicalNonReservedContext(state));
    Optional<WasteDetectorLoopHistory> prior = WasteDetectorHistoryStore.readLoop(state,
        blueprintId, objectMapper);
    String priorContextFingerprint = prior.map(WasteDetectorLoopHistory::lastIterationContextFingerprint)
        .orElse(null);
    Set<String> seenOutputFingerprints = prior.map(WasteDetectorLoopHistory::seenOutputFingerprints)
        .orElseGet(Set::of);

    WasteDetector.evaluateUnchangedLoopContext(blueprintId, blueprintId, iteration,
        contextFingerprint, priorContextFingerprint)
        .ifPresent(signal -> recordWasteSignal(state, signal));

    Set<String> updatedOutputFingerprints = seenOutputFingerprints;
    Optional<String> outputFingerprint = lastStepOutputFingerprint(blueprint, state);
    if (outputFingerprint.isPresent()) {
      WasteDetector.evaluateRepeatedLoopOutput(blueprintId, blueprintId, iteration,
          outputFingerprint.get(), seenOutputFingerprints)
          .ifPresent(signal -> recordWasteSignal(state, signal));
      updatedOutputFingerprints = new HashSet<>(seenOutputFingerprints);
      updatedOutputFingerprints.add(outputFingerprint.get());
    }

    WasteDetectorHistoryStore.writeLoop(state,
        new WasteDetectorLoopHistory(blueprintId, contextFingerprint, updatedOutputFingerprints),
        objectMapper);
  }

  private String canonicalNonReservedContext(WorkflowState state) {
    Map<String, ContextValue> nonReservedContext = state.getContext().entrySet().stream()
        .filter(entry -> !entry.getKey().startsWith("__"))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
            (first, second) -> first,
            () -> new TreeMap<>(String::compareTo)));
    return CanonicalJson.render(objectMapper.valueToTree(nonReservedContext), objectMapper);
  }

  private Optional<String> lastStepOutputFingerprint(BlueprintDefinition blueprint,
      WorkflowState state) {
    List<Executable> steps = blueprint.steps();
    for (int i = steps.size() - 1; i >= 0; i--) {
      if (steps.get(i) instanceof StepDefinition step) {
        Optional<String> output = state.getStepOutput(step.stepId());
        if (output.isPresent()) {
          return Optional.of(ContextFingerprint.of(
              WasteDetector.normalizeOutput(output.get(), objectMapper)));
        }
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private void recordWasteSignal(WorkflowState state, TokenGovernanceSignal signal) {
    wasteSignalPolicy.onSignal(signal);
    String agentIdPart = signal.agentId() != null ? " agentId=%s".formatted(signal.agentId()) : "";
    eventRecorder.record(state.getRunId(), signal.stepId(), WorkflowEventType.TOKEN_GOVERNANCE_SIGNAL,
        "kind=%s%s detail=%s".formatted(signal.kind(), agentIdPart, signal.detail()), "runtime");
  }

  /**
   * Runs a single bounded iteration of the body: advances the loop cursor, logs, and delegates to
   * {@link #executeIteration}. Shared by the strategies so the per-iteration bookkeeping lives in
   * one place.
   */
  protected ExecutionOutcome runIteration(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, int iteration) {
    markLoopIterationStart(executionContext, blueprint.blueprintId(), iteration);
    LOG.log(System.Logger.Level.DEBUG,
        "Loop iteration start strategy={0}, iteration={1}, maxIterations={2}",
        strategy(), iteration, config.maxIterations());
    ExecutionOutcome outcome = executeIteration(blueprint, iteration, executionContext);
    LOG.log(System.Logger.Level.DEBUG, "Loop iteration complete strategy={0}, iteration={1}, outcome={2}",
        strategy(), iteration, outcome);
    return outcome;
  }

  /**
   * Runs the body up to {@code maxIterations}, ending early when {@code terminationCheck} signals
   * after an iteration. Returns immediately on a paused iteration (cursor preserved for resume),
   * clears the cursor and returns on failure or cancellation, and — on a clean termination signal —
   * clears the cursor, logs the {@code terminationReason}, and completes. When the iteration ceiling
   * is reached without a signal, {@link MaxIterationsHandler} decides the outcome.
   *
   * <p>Shared by strategies whose termination is decided per iteration (agent signal, evaluator).
   * The per-iteration agent completion signal is cleared before each iteration so the check observes
   * only the current iteration's body.
   */
  protected ExecutionOutcome iterateUntilSignalled(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, IterationTerminationCheck terminationCheck,
      String terminationReason) {
    WorkflowState state = executionContext.getState();
    String blueprintId = blueprint.blueprintId();
    int start = firstLoopIterationToRun(state, blueprintId);
    for (int iteration = start; iteration <= config.maxIterations(); iteration++) {
      executionContext.setAgentCompletionSignalled(false);
      ExecutionOutcome outcome = runIteration(blueprint, config, executionContext, iteration);
      if (outcome == ExecutionOutcome.PAUSED) {
        return outcome;
      }
      if (outcome == ExecutionOutcome.FAILED) {
        clearLoopIterationCursor(state, blueprintId);
        return outcome;
      }
      if (state.getStatus() == WorkflowStatus.CANCELLED) {
        clearLoopIterationCursor(state, blueprintId);
        return ExecutionOutcome.PAUSED;
      }
      if (terminationCheck.shouldTerminate(iteration)) {
        clearLoopIterationCursor(state, blueprintId);
        LOG.log(System.Logger.Level.INFO,
            "Loop terminated strategy={0}, iterations={1}, reason={2}",
            strategy(), iteration, terminationReason);
        return ExecutionOutcome.COMPLETED;
      }
    }
    return handleMaxIterations(blueprint, config, executionContext, state, blueprintId);
  }

  private ExecutionOutcome handleMaxIterations(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext, WorkflowState state, String blueprintId) {
    ExecutionOutcome bounded = maxIterationsHandler.handle(blueprint, config, executionContext);
    if (bounded == ExecutionOutcome.FAILED) {
      clearLoopIterationCursor(state, blueprintId);
    }
    return bounded;
  }

  /**
   * Decides, after an iteration, whether a per-iteration loop should terminate cleanly.
   */
  @FunctionalInterface
  protected interface IterationTerminationCheck {

    boolean shouldTerminate(int iteration);
  }
}
