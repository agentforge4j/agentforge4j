// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;
import java.util.List;
import org.apache.commons.lang3.math.NumberUtils;

public final class RetryPreviousBehaviourHandler implements
    BehaviourHandler<RetryPreviousBehaviour> {

  private static final System.Logger LOG = System.getLogger(
      RetryPreviousBehaviourHandler.class.getName());

  private static final String RETRY_COUNTER_PREFIX = "__retry_";
  private static final String RETRY_COUNTER_SUFFIX = "_attempts";

  private final EventRecorder eventRecorder;
  private ExecutableExecutor executableExecutor;

  public RetryPreviousBehaviourHandler(EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  public void setExecutableExecutor(ExecutableExecutor executableExecutor) {
    this.executableExecutor = Validate.notNull(executableExecutor,
        "executableExecutor must not be null");
  }

  @Override
  public Class<RetryPreviousBehaviour> behaviourType() {
    return RetryPreviousBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step,
      RetryPreviousBehaviour behaviour,
      ExecutionContext executionContext) {
    Validate.notNull(executableExecutor,
        "executableExecutor must be configured on RetryPreviousBehaviourHandler");
    WorkflowState state = executionContext.getState();
    String attemptKey = RETRY_COUNTER_PREFIX + behaviour.retryStepId() + RETRY_COUNTER_SUFFIX;

    int attempts = readAttemptCount(state, attemptKey);
    LOG.log(System.Logger.Level.INFO,
        "Retry behaviour start stepId={0}, retryStepId={1}, attempt={2}, maxAttempts={3}",
        step.stepId(), behaviour.retryStepId(), attempts + 1, behaviour.maxAttempts());
    if (attempts >= behaviour.maxAttempts()) {
      LOG.log(System.Logger.Level.WARNING, "Retry max attempts reached stepId={0}, retryStepId={1}",
          step.stepId(), behaviour.retryStepId());
      eventRecorder.record(state.getRunId(), step.stepId(),
          WorkflowEventType.STEP_RETRIED,
          "maxAttempts %d reached for retryStepId '%s', executing fallback"
              .formatted(behaviour.maxAttempts(), behaviour.retryStepId()),
          "runtime");
      return executableExecutor.execute(behaviour.fallback(), executionContext);
    }

    Integer retryUid = state.getStepExecutionUid().get(behaviour.retryStepId());
    Validate.notNull(retryUid, () -> new StepExecutionException(
        "RetryPreviousBehaviour in step '%s' references step '%s' which has not been executed yet"
            .formatted(step.stepId(), behaviour.retryStepId())));

    attempts++;
    state.putContextValue(attemptKey, new StringContextValue(String.valueOf(attempts)));

    state.clearEntriesFromUid(retryUid);
    LOG.log(System.Logger.Level.DEBUG, "Retry clearFromUid retryUid={0}", retryUid);
    LOG.log(System.Logger.Level.DEBUG, "Retry dispatched retryMode={0}, retryStepId={1}",
        behaviour.retryMode(), behaviour.retryStepId());
    ExecutionOutcome outcome = switch (behaviour.retryMode()) {
      case SINGLE_STEP -> executeSingleStep(step, behaviour, executionContext);
      case FROM_STEP -> executeFromStep(step, behaviour, executionContext);
    };

    eventRecorder.record(state.getRunId(), step.stepId(),
        WorkflowEventType.STEP_RETRIED,
        "attempt %d of %d, retryMode=%s, retryStepId='%s'"
            .formatted(attempts, behaviour.maxAttempts(),
                behaviour.retryMode(), behaviour.retryStepId()),
        "runtime");

    return outcome;
  }

  private ExecutionOutcome executeSingleStep(StepDefinition step,
      RetryPreviousBehaviour behaviour,
      ExecutionContext executionContext) {
    List<String> orderedIds = executionContext.getCurrentSequenceStepIds();
    Executable target = resolveExecutable(behaviour.retryStepId(), orderedIds, executionContext,
        step.stepId());
    return executableExecutor.execute(target, executionContext);
  }

  private ExecutionOutcome executeFromStep(StepDefinition step,
      RetryPreviousBehaviour behaviour,
      ExecutionContext executionContext) {
    List<String> orderedIds = executionContext.getCurrentSequenceStepIds();
    int fromIndex = orderedIds.indexOf(behaviour.retryStepId());
    int toIndex = orderedIds.indexOf(step.stepId());

    Validate.isGreaterThan(fromIndex, -1, () -> new StepExecutionException(
        "RetryPreviousBehaviour step '%s': retryStepId '%s' not found in current sequence"
            .formatted(step.stepId(), behaviour.retryStepId())));
    Validate.isGreaterThan(toIndex, -1, () -> new StepExecutionException(
        "RetryPreviousBehaviour step '%s': owning step not found in current sequence"
            .formatted(step.stepId())));
    Validate.isTrue(fromIndex < toIndex, () -> new StepExecutionException(
        "RetryPreviousBehaviour step '%s': retryStepId '%s' must appear before owning step in current sequence"
            .formatted(step.stepId(), behaviour.retryStepId())));

    ExecutionOutcome last = ExecutionOutcome.COMPLETED;
    for (String rangeStepId : orderedIds.subList(fromIndex, toIndex)) {
      Executable target = resolveExecutable(rangeStepId, orderedIds, executionContext,
          step.stepId());
      last = executableExecutor.execute(target, executionContext);
      if (last != ExecutionOutcome.COMPLETED) {
        return last;
      }
    }
    return last;
  }

  private Executable resolveExecutable(String stepId,
      List<String> orderedIds,
      ExecutionContext executionContext,
      String owningStepId) {
    Validate.notNull(orderedIds, "orderedIds must not be null");
    Executable target = executionContext.getCurrentSequenceExecutables().get(stepId);
    Validate.notNull(target, () -> new StepExecutionException(
        "RetryPreviousBehaviour step '%s': cannot resolve Executable for stepId '%s'"
            .formatted(owningStepId, stepId)));
    return target;
  }

  private static int readAttemptCount(WorkflowState state, String attemptKey) {
    ContextValue existing = state.getContext().get(attemptKey);
    if (existing == null) {
      return 0;
    }
    if (existing instanceof StringContextValue stringContextValue) {
      return NumberUtils.toInt(stringContextValue.value(), 0);
    }
    return 0;
  }
}
