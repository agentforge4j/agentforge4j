// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.runtime;

import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.ToolDecision;
import com.agentforge4j.core.workflow.state.WorkflowState;

import java.util.Map;

/**
 * The runtime command model for workflow execution.
 *
 * <p>All inputs to a workflow are collected during execution via the artifact
 * mechanism — {@link #start(String)} takes only the workflow id.
 *
 * <p>Implementations are responsible for persisting state, appending events,
 * guarding against infinite recursion from nested or circular workflows, and driving each executable to completion or a
 * human-in-the-loop pause state.
 *
 * <p>Workflow configuration controls the execution flow; AI/model output provides commands or
 * content but does not own runtime flow control.
 */
public interface WorkflowRuntime {

  /**
   * Start a new run of the given workflow.
   *
   * @param workflowId id of a workflow known to the {@code WorkflowRepository}
   *
   * @return the newly-created run id
   */
  String start(String workflowId);

  /**
   * Advance a paused run. Valid when the run is in {@code PAUSED} status.
   *
   * @param runId   id of the run to advance
   * @param actorId Opaque identifier supplied by the embedding application representing the entity responsible for the
   *                action. AgentForge4j treats the value as opaque and does not interpret its structure or meaning.
   */
  void continueRun(String runId, String actorId);

  /**
   * Retry the given step on the given run. Honours the step's {@code RetryPolicy}: an AGENT or SPAR target is retried
   * only when its policy's {@code allowRetry} is {@code true} and the number of attempts already made against that
   * step's shared attempt budget — shared between this verb and any {@code RETRY_PREVIOUS} step targeting the same
   * step — is still under the policy's {@code maxAttempts} ceiling; a granted retry consumes one unit of that budget.
   * An AGENT/SPAR step that declares <strong>no</strong> policy defaults to {@code RetryPolicy.none()} and is
   * therefore <strong>not retryable at all</strong> through this verb (fail-closed). A step type with no
   * {@code RetryPolicy} concept (anything other than AGENT/SPAR) is unrestricted. A rejected retry throws
   * {@link IllegalStateException} and leaves the run untouched.
   *
   * <p>{@code stepId} must name a <strong>top-level</strong> step — one that appears directly in the
   * workflow's top-level sequence. The run is repositioned at that step and the sequence is re-driven, so the target
   * and every step after it execute again and the run finalises on the real downstream outcome (it may complete, pause,
   * or fail). A step that exists only nested inside a blueprint or sub-workflow is rejected; retry its enclosing
   * top-level step instead.
   *
   * @param runId   id of the run
   * @param stepId  id of the top-level step to retry
   * @param actorId Opaque identifier supplied by the embedding application representing the entity responsible for the
   *                action. AgentForge4j treats the value as opaque and does not interpret its structure or meaning.
   *
   * @throws IllegalStateException if the target's {@code RetryPolicy} forbids operator retry
   *                               ({@code allowRetry=false} — including the undeclared-policy {@code RetryPolicy.none()}
   *                               default), or its shared {@code maxAttempts} ceiling is already reached
   */
  void retry(String runId, String stepId, String actorId);

  /**
   * Provide human approval for a step that requires it.
   *
   * @param runId        id of the run
   * @param stepId       id of the approved step
   * @param approverNote human-readable note recorded in the event log
   * @param actorId      Opaque identifier supplied by the embedding application representing the entity responsible for
   *                     the action. AgentForge4j treats the value as opaque and does not interpret its structure or
   *                     meaning.
   */
  void approve(String runId, String stepId, String approverNote, String actorId);

  /**
   * Submit answers to the pending artifact on a run in {@code AWAITING_INPUT} status.
   *
   * <p>Keys are artifact item ids; values are the raw answers. The runtime writes
   * each answer to the shared context under the namespaced key {@code artifactId.itemId}.
   * Additionally, when an answer's item id is one of the current INPUT step's declared output keys,
   * the value is also written under that bare key, so downstream steps and branches that read the
   * declared output key resolve it.
   *
   * @param runId   id of the run
   * @param answers map of artifact item id to user-provided answer
   * @param actorId Opaque identifier supplied by the embedding application representing the entity responsible for the
   *                action. AgentForge4j treats the value as opaque and does not interpret its structure or meaning.
   */
  void submitInput(String runId, Map<String, String> answers, String actorId);

  /**
   * Cancel the given run.
   *
   * @param runId   id of the run
   * @param actorId Opaque identifier supplied by the embedding application representing the entity responsible for the
   *                action. AgentForge4j treats the value as opaque and does not interpret its structure or meaning.
   */
  void cancel(String runId, String actorId);

  /**
   * Submit a forward-only review for a step suspended in
   * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_REVIEW} by a {@code HUMAN_REVIEW} gate. Records
   * the note and advances the run; this is not an approve/reject.
   *
   * @param runId      id of the run
   * @param stepId     id of the reviewed step
   * @param reviewNote human-readable note recorded in the event log; may be blank
   * @param actorId    Opaque identifier supplied by the embedding application representing the entity responsible for
   *                   the action. AgentForge4j treats the value as opaque and does not interpret its structure or
   *                   meaning.
   */
  void submitReview(String runId, String stepId, String reviewNote, String actorId);

  /**
   * Decide a step suspended in {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_STEP_APPROVAL} by a
   * {@code HUMAN_APPROVAL} gate. {@link StepApprovalDecision.Approve} advances the run;
   * {@link StepApprovalDecision.Reject} fails it (no send-back). The actor is carried inside the decision.
   *
   * @param runId    id of the run
   * @param stepId   id of the step being decided
   * @param decision the approve/reject decision
   */
  void decideStepApproval(String runId, String stepId, StepApprovalDecision decision);

  /**
   * @param runId id of the run
   *
   * @return a defensive snapshot of the current state — mutating the returned object does not alter persisted runtime
   * state
   */
  WorkflowState getState(String runId);

  /**
   * Resume a run suspended awaiting approval of a tool invocation, applying the human decision.
   *
   * <p>Distinct from {@link #approve(String, String, String, String)} (which resumes a step and may
   * re-invoke the agent): this delegates to the tool-execution service's resume path — re-resolving and invoking the
   * exact approved tool without re-invoking the LLM — and applies the outcome to state. An approved invocation that
   * executes successfully, or a rejected one, advances past the requesting step. An approved invocation whose call
   * <em>fails</em> (resolution, validation, or the provider itself) does not advance: a fresh pending row (origin
   * {@code EXECUTION_FAILED}) is persisted and the run re-suspends in
   * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_TOOL_DECISION}, giving the operator a further
   * decision point via {@link #resolveToolDecision}. Valid only when the run is in
   * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_TOOL_APPROVAL}.
   *
   * @param runId            id of the run
   * @param toolInvocationId id of the pending tool invocation being decided
   * @param decision         the human approve/reject decision
   *
   * @return a snapshot of the resumed run state
   *
   * @throws com.agentforge4j.core.spi.tool.PolicyDenialTerminalException if {@code decision} is an
   *                                    {@link ApprovalDecision.Approve} against a policy-denied pending invocation —
   *                                    a policy denial is terminal for that invocation and is never executed; no run
   *                                    state is mutated
   * @throws com.agentforge4j.core.spi.tool.ToolInvocationClaimLostException if a concurrent resume already claimed or
   *                                    replaced the pending invocation; a benign concurrency-loss signal — no run
   *                                    state is mutated
   */
  WorkflowState continueAfterToolApproval(String runId, String toolInvocationId,
      ApprovalDecision decision);

  /**
   * Resolve a run suspended in {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_TOOL_DECISION} after
   * a tool invocation was denied by policy or failed after retries, applying the operator's decision.
   *
   * <p>{@link ToolDecision.Continue} proceeds without the tool result, writing
   * {@code tool.<capability>.error} to context and advancing past the requesting step. {@link ToolDecision.Retry}
   * replays the exact stored call without re-invoking the LLM: a successful replay applies the tool result and
   * advances the requesting step, but a replay that fails again does <em>not</em> advance — a fresh pending row
   * (origin {@code EXECUTION_FAILED}) is persisted and the run re-suspends in {@code AWAITING_TOOL_DECISION} for a
   * further decision. {@code Retry} against a pending invocation that was denied by policy is invalid: a denial is
   * terminal for that invocation, so the call throws without invoking the provider or mutating run state, leaving the
   * pending row intact for a later {@link ToolDecision.Continue}. Valid only when the run is in
   * {@code AWAITING_TOOL_DECISION}; use {@link #approve(String, String, String, String)} for escalation approvals instead.
   *
   * @param runId            id of the run
   * @param toolInvocationId id of the pending tool invocation being resolved
   * @param decision         the operator continue/retry decision
   *
   * @return a snapshot of the resumed run state
   *
   * @throws com.agentforge4j.core.spi.tool.PolicyDenialTerminalException if {@code decision} is a
   *                                    {@link ToolDecision.Retry} against a policy-denied pending invocation; no run
   *                                    state is mutated
   * @throws com.agentforge4j.core.spi.tool.ToolInvocationClaimLostException if a concurrent resolution already claimed
   *                                    or replaced the pending invocation; a benign concurrency-loss signal — no run
   *                                    state is mutated
   */
  WorkflowState resolveToolDecision(String runId, String toolInvocationId, ToolDecision decision);
}
