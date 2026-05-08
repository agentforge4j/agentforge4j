package com.agentforge4j.core.runtime;

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
   * @return the current state
   */
  WorkflowState getState(String runId);
}
