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
 * guarding against infinite recursion from nested or circular workflows, and driving each
 * executable to completion or a human-in-the-loop pause state.
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
   * @param runId id of the run to advance
   */
  void continueRun(String runId);

  /**
   * Retry the given step on the given run. Honours the step's {@code RetryPolicy}.
   *
   * @param runId  id of the run
   * @param stepId id of the step to retry
   */
  void retry(String runId, String stepId);

  /**
   * Provide human approval for a step that requires it.
   *
   * @param runId        id of the run
   * @param stepId       id of the approved step
   * @param approverNote human-readable note recorded in the event log
   */
  void approve(String runId, String stepId, String approverNote);

  /**
   * Submit answers to the pending artifact on a run in {@code AWAITING_INPUT} status.
   *
   * <p>Keys are artifact item ids; values are the raw answers. The runtime writes
   * each answer to the shared context under the key {@code artifactId.itemId}.
   *
   * @param runId   id of the run
   * @param answers map of artifact item id to user-provided answer
   */
  void submitInput(String runId, Map<String, String> answers);

  /**
   * Cancel the given run.
   *
   * @param runId id of the run
   */
  void cancel(String runId);

  /**
   * @param runId id of the run
   *
   * @return a defensive snapshot of the current state — mutating the returned object does not alter
   * persisted runtime state
   */
  WorkflowState getState(String runId);

  /**
   * Resume a run suspended awaiting approval of a tool invocation, applying the human decision.
   *
   * <p>Distinct from {@link #approve(String, String, String)} (which resumes a step and may
   * re-invoke the agent): this delegates to the tool-execution service's resume path — re-resolving
   * and invoking the exact approved tool without re-invoking the LLM — applies the outcome to
   * state, and advances past the requesting step. Valid only when the run is in
   * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_TOOL_APPROVAL}.
   *
   * @param runId            id of the run
   * @param toolInvocationId id of the pending tool invocation being decided
   * @param decision         the human approve/reject decision
   *
   * @return a snapshot of the resumed run state
   */
  WorkflowState continueAfterToolApproval(String runId, String toolInvocationId,
      ApprovalDecision decision);

  /**
   * Resolve a run suspended in
   * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_TOOL_DECISION} after a tool
   * invocation was denied by policy or failed after retries, applying the operator's decision.
   *
   * <p>{@link ToolDecision.Continue} proceeds without the tool result, writing
   * {@code tool.<capability>.error} to context; {@link ToolDecision.Retry} replays the exact stored
   * call without re-invoking the LLM. Either way the requesting step is advanced. Valid only when
   * the run is in {@code AWAITING_TOOL_DECISION}; use {@link #approve(String, String, String)} for
   * escalation approvals instead.
   *
   * @param runId            id of the run
   * @param toolInvocationId id of the pending tool invocation being resolved
   * @param decision         the operator continue/retry decision
   *
   * @return a snapshot of the resumed run state
   */
  WorkflowState resolveToolDecision(String runId, String toolInvocationId, ToolDecision decision);
}
