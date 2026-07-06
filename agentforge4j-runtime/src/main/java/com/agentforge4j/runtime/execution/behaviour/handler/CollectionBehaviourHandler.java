// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.collection.CollectionPhase;
import com.agentforge4j.core.workflow.collection.CollectionState;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.util.Validate;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Handles a {@link CollectionBehaviour}: opens a collection gate on first entry by creating an
 * {@link CollectionState} in {@link CollectionPhase#OPEN}, flips the run to
 * {@link WorkflowStatus#AWAITING_COLLECTION}, emits {@link WorkflowEventType#COLLECTION_OPENED}, and
 * returns {@link ExecutionOutcome#PAUSED}.
 *
 * <p>Idempotent on replay: if an {@code OPEN} state already exists for the step (a re-drive that has
 * not yet advanced past an open gate) it re-pauses without creating new state or re-emitting the open
 * event. Submissions, close, and reopen are driven through {@code CollectionGateRuntime}, not this
 * handler.
 *
 * <p>{@code CollectionState} is intentionally never cleared by a retry or rewind (a closed collection
 * must never reopen), so re-entering a step whose gate is already {@code CLOSED} is not a normal
 * replay: it means a retry/rewind targeted a step this run already collected and closed. Rather than
 * silently re-pausing on stale, permanently-closed state, this fails the step loudly.
 */
public final class CollectionBehaviourHandler implements BehaviourHandler<CollectionBehaviour> {

  private static final System.Logger LOG =
      System.getLogger(CollectionBehaviourHandler.class.getName());

  private final EventRecorder eventRecorder;
  private final Clock clock;

  public CollectionBehaviourHandler(EventRecorder eventRecorder, Clock clock) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
  }

  @Override
  public Class<CollectionBehaviour> behaviourType() {
    return CollectionBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step, CollectionBehaviour behaviour,
      ExecutionContext executionContext) {
    WorkflowState state = executionContext.getState();
    String stepId = step.stepId();
    Optional<CollectionState> existing = state.getCollectionState(stepId);
    if (existing.isPresent() && existing.get().phase() == CollectionPhase.CLOSED) {
      throw new IllegalStateException(
          ("Collection step '%s' cannot be re-entered: its gate is already closed and a closed "
              + "collection is never reopened by retry or rewind")
              .formatted(stepId));
    }
    if (existing.isEmpty()) {
      LOG.log(System.Logger.Level.INFO, "Opening collection gate stepId={0}", stepId);
      state.putCollectionState(new CollectionState(stepId, CollectionPhase.OPEN, clock.instant(),
          List.of(), null, null, null, Set.of(), Set.of(), 0L));
      eventRecorder.record(state.getRunId(), stepId, WorkflowEventType.COLLECTION_OPENED,
          openSummary(behaviour), "runtime");
    }
    state.setStatus(WorkflowStatus.AWAITING_COLLECTION);
    state.setLastUpdatedAt(clock.instant());
    return ExecutionOutcome.PAUSED;
  }

  private static String openSummary(CollectionBehaviour behaviour) {
    return "{\"minItems\":%d,\"maxItems\":%s,\"authorizationMode\":\"%s\"}".formatted(
        behaviour.minItems(),
        behaviour.maxItems() == null ? "null" : behaviour.maxItems().toString(),
        behaviour.authorizationMode());
  }
}
