// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.scenario;

import com.agentforge4j.util.Validate;
import java.util.Map;

/**
 * A scripted human response that {@link com.agentforge4j.testkit.harness.WorkflowTestHarness} drains
 * at each human-in-the-loop pause to drive a run forward deterministically. One response is consumed
 * per pause, in queue order; when the queue is exhausted the run is left at whatever state it last
 * reached (terminal or still paused), so a scenario may also assert a meaningful pending state.
 *
 * <p>The variants map one-to-one onto the runtime resume verbs: {@link Input} →
 * {@code submitInput}, {@link Review} → {@code submitReview}, {@link StepApproval} →
 * {@code decideStepApproval}, {@link Escalation} → {@code approve}. Tool-gate resumes
 * ({@code continueAfterToolApproval} / {@code resolveToolDecision}) extend this sealed set when the
 * tool tier needs them — the harness drive loop is the shared, reusable surface.
 */
public sealed interface GateResponse
    permits GateResponse.Input, GateResponse.Review, GateResponse.StepApproval,
    GateResponse.Escalation, GateResponse.ToolApproval, GateResponse.ToolDecision {

  /**
   * Answers for an {@code AWAITING_INPUT} pause, keyed by artifact item id.
   *
   * @param answers item-id → answer map; must not be {@code null} (defensively copied)
   */
  record Input(Map<String, String> answers) implements GateResponse {

    /**
     * Validates and defensively copies the answers.
     *
     * @param answers item-id → answer map; must not be {@code null}
     */
    public Input {
      Validate.notNull(answers, "Input answers must not be null");
      answers = Map.copyOf(answers);
    }
  }

  /**
   * A forward-only note for an {@code AWAITING_REVIEW} pause (a {@code HUMAN_REVIEW} gate).
   *
   * @param note review note recorded on the event; may be blank
   */
  record Review(String note) implements GateResponse {
  }

  /**
   * An approve/reject decision for an {@code AWAITING_STEP_APPROVAL} pause (a {@code HUMAN_APPROVAL}
   * gate). A rejection fails the run.
   *
   * @param approve {@code true} to approve and advance; {@code false} to reject and fail
   * @param note    approval note or rejection reason recorded on the event; may be blank
   */
  record StepApproval(boolean approve, String note) implements GateResponse {
  }

  /**
   * An approval note for an {@code AWAITING_APPROVAL} pause raised by an {@code ESCALATE} command.
   *
   * @param note approver note recorded on the event; may be blank
   */
  record Escalation(String note) implements GateResponse {
  }

  /**
   * An approve/reject decision for an {@code AWAITING_TOOL_APPROVAL} pause (tool policy
   * {@code RequireApproval}). On approval the stored tool call is invoked and the run continues; on
   * rejection the call is denied.
   *
   * @param toolInvocationId id of the pending tool invocation to resolve, or {@code null} to
   *                         auto-target the run's single current pending invocation (the harness
   *                         fails closed when there is not exactly one)
   * @param approve          {@code true} to approve and invoke; {@code false} to reject
   * @param reason           rejection reason recorded when not approved; may be blank
   */
  record ToolApproval(String toolInvocationId, boolean approve, String reason)
      implements GateResponse {

    /**
     * Validates the invocation id (only when an explicit one is given; {@code null} means
     * auto-target).
     *
     * @param toolInvocationId id of the pending tool invocation, or {@code null} to auto-target
     * @param approve          {@code true} to approve
     * @param reason           rejection reason; may be blank
     */
    public ToolApproval {
      if (toolInvocationId != null) {
        Validate.notBlank(toolInvocationId,
            "ToolApproval toolInvocationId must not be blank when provided");
      }
    }
  }

  /**
   * A continue/retry decision for an {@code AWAITING_TOOL_DECISION} pause (tool policy {@code Deny}
   * or a failed invocation). {@code continue} proceeds without a tool result; {@code retry} replays
   * the stored call.
   *
   * @param toolInvocationId id of the pending tool invocation to resolve, or {@code null} to
   *                         auto-target the run's single current pending invocation (the harness
   *                         fails closed when there is not exactly one)
   * @param retry            {@code true} to retry the stored call; {@code false} to continue without
   *                         a result
   */
  record ToolDecision(String toolInvocationId, boolean retry) implements GateResponse {

    /**
     * Validates the invocation id (only when an explicit one is given; {@code null} means
     * auto-target).
     *
     * @param toolInvocationId id of the pending tool invocation, or {@code null} to auto-target
     * @param retry            {@code true} to retry
     */
    public ToolDecision {
      if (toolInvocationId != null) {
        Validate.notBlank(toolInvocationId,
            "ToolDecision toolInvocationId must not be blank when provided");
      }
    }
  }

  /**
   * Creates an {@link Input} response.
   *
   * @param answers item-id → answer map
   *
   * @return the response
   */
  static GateResponse input(Map<String, String> answers) {
    return new Input(answers);
  }

  /**
   * Creates a {@link Review} response.
   *
   * @param note review note; may be blank
   *
   * @return the response
   */
  static GateResponse review(String note) {
    return new Review(note);
  }

  /**
   * Creates an approving {@link StepApproval} response.
   *
   * @param note approval note; may be blank
   *
   * @return the response
   */
  static GateResponse approveStep(String note) {
    return new StepApproval(true, note);
  }

  /**
   * Creates a rejecting {@link StepApproval} response.
   *
   * @param reason rejection reason; may be blank
   *
   * @return the response
   */
  static GateResponse rejectStep(String reason) {
    return new StepApproval(false, reason);
  }

  /**
   * Creates an {@link Escalation} response.
   *
   * @param note approver note; may be blank
   *
   * @return the response
   */
  static GateResponse escalationApproval(String note) {
    return new Escalation(note);
  }

  /**
   * Creates an approving {@link ToolApproval} that auto-targets the run's single current pending
   * tool invocation (the harness fails closed when there is not exactly one).
   *
   * @return the response
   */
  static GateResponse toolApprove() {
    return new ToolApproval(null, true, "");
  }

  /**
   * Creates an approving {@link ToolApproval} response targeting an explicit invocation id.
   *
   * @param toolInvocationId id of the pending tool invocation to approve
   *
   * @return the response
   */
  static GateResponse toolApprove(String toolInvocationId) {
    return new ToolApproval(toolInvocationId, true, "");
  }

  /**
   * Creates a rejecting {@link ToolApproval} that auto-targets the run's single current pending tool
   * invocation (the harness fails closed when there is not exactly one).
   *
   * @param reason rejection reason; may be blank
   *
   * @return the response
   */
  static GateResponse toolReject(String reason) {
    return new ToolApproval(null, false, reason);
  }

  /**
   * Creates a rejecting {@link ToolApproval} response targeting an explicit invocation id.
   *
   * @param toolInvocationId id of the pending tool invocation to reject
   * @param reason           rejection reason; may be blank
   *
   * @return the response
   */
  static GateResponse toolReject(String toolInvocationId, String reason) {
    return new ToolApproval(toolInvocationId, false, reason);
  }

  /**
   * Creates a continue {@link ToolDecision} that auto-targets the run's single current pending tool
   * invocation (the harness fails closed when there is not exactly one).
   *
   * @return the response
   */
  static GateResponse toolContinue() {
    return new ToolDecision(null, false);
  }

  /**
   * Creates a continue {@link ToolDecision} response targeting an explicit invocation id.
   *
   * @param toolInvocationId id of the pending tool invocation to resolve
   *
   * @return the response
   */
  static GateResponse toolContinue(String toolInvocationId) {
    return new ToolDecision(toolInvocationId, false);
  }

  /**
   * Creates a retry {@link ToolDecision} that auto-targets the run's single current pending tool
   * invocation (the harness fails closed when there is not exactly one).
   *
   * @return the response
   */
  static GateResponse toolRetry() {
    return new ToolDecision(null, true);
  }

  /**
   * Creates a retry {@link ToolDecision} response targeting an explicit invocation id.
   *
   * @param toolInvocationId id of the pending tool invocation to resolve
   *
   * @return the response
   */
  static GateResponse toolRetry(String toolInvocationId) {
    return new ToolDecision(toolInvocationId, true);
  }
}
