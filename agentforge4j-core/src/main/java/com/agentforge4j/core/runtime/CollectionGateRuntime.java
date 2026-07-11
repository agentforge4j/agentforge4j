// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.runtime;

/**
 * Operations on a collection gate — a step suspended in
 * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_COLLECTION} that accepts zero or
 * more submissions over time until explicitly closed (and optionally reopened).
 *
 * <p>Segregated from {@link WorkflowRuntime} (ISP): generic lifecycle verbs ({@code continueRun},
 * {@code cancel}, {@code retry}) stay on {@code WorkflowRuntime}; this surface is cohesive and optional.
 * All verbs take the opaque effective {@code actorId} supplied by the embedding application, consistent
 * with the resume verbs. Guarded operations are authorized per the gate's authorization mode; a denial
 * throws {@link com.agentforge4j.core.workflow.collection.CollectionAuthorizationException}.
 */
public interface CollectionGateRuntime {

  /**
   * Submits a new item into an open collection gate.
   *
   * @param runId      the run id
   * @param stepId     the collection step id
   * @param submission the submission payload and optional idempotency/dedupe keys
   * @param actorId    the opaque effective actor; must not be blank
   *
   * @return the submission outcome (accepted, idempotent, or rejected by a constraint)
   */
  SubmissionResult submitItem(String runId, String stepId, CollectionSubmission submission,
      String actorId);

  /**
   * Replaces an existing item slot with a new version, subject to the gate's replacement policy.
   *
   * @param runId        the run id
   * @param stepId       the collection step id
   * @param submissionId the slot to replace
   * @param replacement  the replacement payload
   * @param actorId      the opaque effective actor; must not be blank
   *
   * @return the replacement outcome
   */
  SubmissionResult replaceItem(String runId, String stepId, String submissionId,
      CollectionSubmission replacement, String actorId);

  /**
   * Withdraws an existing item slot, subject to the gate's withdrawal policy.
   *
   * @param runId        the run id
   * @param stepId       the collection step id
   * @param submissionId the slot to withdraw
   * @param actorId      the opaque effective actor; must not be blank
   */
  void withdrawItem(String runId, String stepId, String submissionId, String actorId);

  /**
   * Closes the gate. When the reopen policy is {@code NONE} the run advances; when {@code ALLOWED} the
   * run is held at the gate (closed) until {@link WorkflowRuntime#continueRun} advances it, leaving a
   * window in which {@link #reopenCollection} is possible.
   *
   * @param runId   the run id
   * @param stepId  the collection step id
   * @param request the close request (actor, reason, override, idempotency token)
   *
   * @return the close outcome
   */
  CloseResult closeCollection(String runId, String stepId, CloseRequest request);

  /**
   * Reopens a closed gate that has not yet continued, subject to the gate's reopen policy.
   *
   * @param runId   the run id
   * @param stepId  the collection step id
   * @param actorId the opaque effective actor; must not be blank
   */
  void reopenCollection(String runId, String stepId, String actorId);

  /**
   * Returns a read view of the gate for an authorized viewer.
   *
   * <p>Remains readable once the run reaches a terminal status such as {@code COMPLETED} or
   * {@code FAILED} — the materialized collection is part of the run's audit trail and stays
   * available for review after the run finishes. A {@code CANCELLED} run is the one terminal
   * status this excludes: its collection is no longer readable through this method.
   *
   * @param runId   the run id
   * @param stepId  the collection step id
   * @param actorId the opaque effective actor; must not be blank
   *
   * @return the current view of the gate
   */
  CollectionView getCollection(String runId, String stepId, String actorId);
}
