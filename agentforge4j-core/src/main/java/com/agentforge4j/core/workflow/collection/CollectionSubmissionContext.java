// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import com.agentforge4j.util.Validate;

/**
 * Read-only context handed to a {@link CollectionSubmissionValidator} for one submit or replace
 * attempt at a collection gate. Carries the run/step coordinates, the acting identity, the
 * submitted payload with its client-supplied tokens, the gate's declared configuration, and the
 * gate's current state so an embedding application can apply its own submission policy.
 *
 * @param runId                 id of the suspended run; never blank
 * @param workflowId            id of the workflow definition the run executes; never blank
 * @param stepId                id of the collection step; never blank
 * @param actorId               opaque id of the submitting actor; never blank
 * @param payload               the submitted item payload; never {@code null}
 * @param clientToken           client-supplied idempotency token; {@code null} when absent
 * @param dedupeKey             client-supplied dedupe key; {@code null} when absent
 * @param replacesSubmissionId  id of the slot being replaced; {@code null} for a new submission
 * @param behaviour             the gate's declared configuration; never {@code null}
 * @param collection            the gate's current state (live items, phase, seen tokens); never
 *                              {@code null}
 */
public record CollectionSubmissionContext(
    String runId,
    String workflowId,
    String stepId,
    String actorId,
    CollectionPayload payload,
    String clientToken,
    String dedupeKey,
    String replacesSubmissionId,
    CollectionBehaviour behaviour,
    CollectionState collection
) {

  public CollectionSubmissionContext {
    Validate.notBlank(runId, "CollectionSubmissionContext runId must not be blank");
    Validate.notBlank(workflowId, "CollectionSubmissionContext workflowId must not be blank");
    Validate.notBlank(stepId, "CollectionSubmissionContext stepId must not be blank");
    Validate.notBlank(actorId, "CollectionSubmissionContext actorId must not be blank");
    Validate.notNull(payload, "CollectionSubmissionContext payload must not be null");
    Validate.notNull(behaviour, "CollectionSubmissionContext behaviour must not be null");
    Validate.notNull(collection, "CollectionSubmissionContext collection must not be null");
  }
}
