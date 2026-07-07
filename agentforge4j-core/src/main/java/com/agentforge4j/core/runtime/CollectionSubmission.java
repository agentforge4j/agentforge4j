// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.runtime;

import com.agentforge4j.core.workflow.collection.CollectionPayload;
import com.agentforge4j.util.Validate;

/**
 * A submission (or replacement) into a collection gate. The {@code submissionId} is assigned by the
 * runtime, never carried here, to prevent collision or forgery.
 *
 * @param payload     non-null content of the submission
 * @param clientToken optional idempotency token; a repeat returns the original submission with no new
 *                    item and no event. {@code null} when the caller does not need idempotency.
 *                    If the item the token originally carried has since been replaced or withdrawn,
 *                    the repeat still returns {@link SubmissionResult.Status#IDEMPOTENT} but with a
 *                    {@code null} {@code submissionId} — the token is remembered as seen even though
 *                    no current item carries it
 * @param dedupeKey   optional caller-supplied opaque dedupe key honoured under
 *                    {@code DuplicatePolicy.REJECT_BY_DEDUPE_KEY}; {@code null} when absent
 */
public record CollectionSubmission(CollectionPayload payload, String clientToken, String dedupeKey) {

  public CollectionSubmission {
    Validate.notNull(payload, "CollectionSubmission payload must not be null");
  }
}
