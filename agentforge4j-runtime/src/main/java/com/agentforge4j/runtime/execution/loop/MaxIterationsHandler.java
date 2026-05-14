package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.util.Validate;
import java.time.Clock;
import lombok.RequiredArgsConstructor;

/**
 * Encapsulates the policy for reacting to a loop reaching {@code maxIterations}.
 *
 * <p>On {@link MaxIterationsAction#AWAIT_USER} the run is transitioned to
 * {@code PAUSED} so that the UI can surface the situation and let the user decide to continue,
 * change something, or abort.
 *
 * <p>On {@link MaxIterationsAction#FAIL} the run is transitioned to {@code FAILED}.
 */
@RequiredArgsConstructor
public final class MaxIterationsHandler {

  private final EventRecorder eventRecorder;
  private final Clock clock;

  public ExecutionOutcome handle(BlueprintDefinition blueprint,
      LoopConfig config,
      ExecutionContext executionContext) {
    validate(blueprint, config, executionContext);
    WorkflowState state = executionContext.getState();
    String payload = "Loop on blueprint '%s' reached maxIterations=%d".formatted(
        blueprint.blueprintId(), config.maxIterations());

    return switch (config.maxIterationsAction()) {
      case AWAIT_USER -> handleAwaitUser(blueprint, state, payload);
      case FAIL -> handleFailed(blueprint, state, payload);
    };
  }

  private ExecutionOutcome handleFailed(BlueprintDefinition blueprint, WorkflowState state,
      String payload) {
    state.setStatus(WorkflowStatus.FAILED);
    state.setLastUpdatedAt(clock.instant());
    eventRecorder.record(state.getRunId(), blueprint.blueprintId(),
        WorkflowEventType.RUN_FAILED, payload, "runtime");
    return ExecutionOutcome.FAILED;
  }

  private ExecutionOutcome handleAwaitUser(BlueprintDefinition blueprint, WorkflowState state,
      String payload) {
    state.setStatus(WorkflowStatus.PAUSED);
    state.setLastUpdatedAt(clock.instant());
    eventRecorder.record(state.getRunId(), blueprint.blueprintId(),
        WorkflowEventType.LOOP_ITERATION_COMPLETED, payload + " — awaiting user", "runtime");
    return ExecutionOutcome.PAUSED;
  }

  private static void validate(BlueprintDefinition blueprint, LoopConfig config,
      ExecutionContext executionContext) {
    Validate.notNull(blueprint, "blueprint must not be null");
    Validate.notNull(config, "config must not be null");
    Validate.notNull(executionContext, "executionContext must not be null");
  }
}
