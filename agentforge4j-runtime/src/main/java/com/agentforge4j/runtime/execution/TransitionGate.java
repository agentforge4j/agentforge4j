// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.TransitionAware;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;

/**
 * Honours a completed step's {@link com.agentforge4j.core.workflow.step.StepTransition} gate. Read uniformly via
 * {@code instanceof TransitionAware} at every step-completion point. {@code AUTO} advances;
 * {@code HUMAN_REVIEW}/{@code HUMAN_APPROVAL} suspend the run (status set + a {@code STEP_AWAITING_*} event emitted),
 * to be resumed via {@code WorkflowRuntime.submitReview}/{@code decideStepApproval}.
 *
 * <p>The gate runs after the step's output is recorded, so the suspended step is non-re-entrant: on
 * resume the drive loop skips it (presence in {@code stepOutputs}) and continues past it.
 */
public final class TransitionGate {

  private final EventRecorder eventRecorder;

  public TransitionGate(EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  /**
   * Suspends the run when the completed step carries a human gate.
   *
   * @param step  the completed step
   * @param state the live run state
   *
   * @return {@code true} when the run was suspended (the caller must stop advancing); {@code false} when the step is
   * {@code AUTO} (or carries no transition) and the run should advance
   */
  public boolean suspendIfGated(StepDefinition step, WorkflowState state) {
    Validate.notNull(step, "step must not be null");
    Validate.notNull(state, "state must not be null");
    if (!(step.behaviour() instanceof TransitionAware transitionAware)) {
      return false;
    }
    StepTransition transition = transitionAware.transition();
    if (transition == StepTransition.AUTO) {
      return false;
    }
    // Make the suspended step non-re-entrant on resume. Behaviours that record their own step
    // output (AGENT/SPAR/INPUT/tool) already have one; RESOURCE/WORKFLOW do not, so mark them — the
    // drive loop skips any step present in stepOutputs (mirrors the advancePastToolStep marker).
    if (!state.getStepOutputs().containsKey(step.stepId())) {
      state.putStepOutput(step.stepId(), "gated:" + transition.name());
    }
    WorkflowStatus status = transition == StepTransition.HUMAN_REVIEW
        ? WorkflowStatus.AWAITING_REVIEW
        : WorkflowStatus.AWAITING_STEP_APPROVAL;
    WorkflowEventType event = transition == StepTransition.HUMAN_REVIEW
        ? WorkflowEventType.STEP_AWAITING_REVIEW
        : WorkflowEventType.STEP_AWAITING_APPROVAL;
    // Canonicalise the suspended-step identity: a WORKFLOW carrier's nested execution leaves
    // currentStepId pointing at the last inner step, so reset it to the gated step. This is the
    // single signal submitReview/decideStepApproval validate the caller-supplied stepId against.
    state.setCurrentStepId(step.stepId());
    state.setStatus(status);
    eventRecorder.record(state.getRunId(), step.stepId(), event, null, "runtime");
    return true;
  }

  /**
   * Suspends the run when a completed blueprint carries a human post-loop gate. Marks the blueprint so the drive loop
   * skips it on resume (blueprint refs are otherwise always re-entered); the blueprint's body has already completed, so
   * resume advances past it rather than re-running it.
   *
   * <p>The marker is uid-scoped at {@code bodyCompletionUid} (the highest execution uid among the
   * blueprint's body steps), so {@link WorkflowState#clearEntriesFromUid(int)} drops it exactly when a retry/rewind
   * clears the body's execution range — the re-drive then re-runs and re-gates the blueprint instead of silently
   * skipping it over wiped body state.
   *
   * @param ref               the blueprint reference that completed
   * @param behaviour         the blueprint's behaviour (carrying the post-loop transition)
   * @param state             the live run state
   * @param bodyCompletionUid the highest execution uid among the blueprint's body steps; {@code 0} for a degenerate
   *                          empty body (the marker is then not uid-scoped — there is no body state a rewind could
   *                          strand)
   *
   * @return {@code true} when the run was suspended; {@code false} for {@code AUTO}
   */
  public boolean suspendBlueprintIfGated(BlueprintRef ref, BlueprintBehaviour behaviour,
      WorkflowState state, int bodyCompletionUid) {
    Validate.notNull(ref, "ref must not be null");
    Validate.notNull(behaviour, "behaviour must not be null");
    Validate.notNull(state, "state must not be null");
    Validate.isNotNegative(bodyCompletionUid, "bodyCompletionUid must not be negative");
    StepTransition transition = behaviour.transition();
    if (transition == StepTransition.AUTO) {
      return false;
    }
    String marker = blueprintGateMarker(ref.blueprintId());
    if (!state.getStepOutputs().containsKey(marker)) {
      state.putStepOutput(marker, "gated:" + transition.name());
      if (bodyCompletionUid > 0) {
        state.putStepExecutionUid(marker, bodyCompletionUid);
      }
    }
    WorkflowStatus status = transition == StepTransition.HUMAN_REVIEW
        ? WorkflowStatus.AWAITING_REVIEW
        : WorkflowStatus.AWAITING_STEP_APPROVAL;
    WorkflowEventType event = transition == StepTransition.HUMAN_REVIEW
        ? WorkflowEventType.STEP_AWAITING_REVIEW
        : WorkflowEventType.STEP_AWAITING_APPROVAL;
    // Canonicalise the suspended-step identity to the blueprint id (the inner steps left
    // currentStepId pointing at the last one), so submitReview/decideStepApproval can validate the
    // caller-supplied stepId against currentStepId uniformly with the step-gate path.
    state.setCurrentStepId(ref.blueprintId());
    state.setStatus(status);
    eventRecorder.record(state.getRunId(), ref.blueprintId(), event, null, "runtime");
    return true;
  }

  /**
   * The {@code stepOutputs} key that marks a gated blueprint as satisfied, so the drive loop skips it on resume. Shared
   * with {@code StepSequenceExecutor}.
   *
   * @param blueprintId the blueprint id
   *
   * @return the marker key
   */
  public static String blueprintGateMarker(String blueprintId) {
    return "blueprint:" + blueprintId + ":gated";
  }
}
