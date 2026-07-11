// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.runtime;

/**
 * Outcome of a submit or replace operation on a collection gate.
 *
 * @param status       the outcome classification
 * @param submissionId the slot id; set for {@link Status#ACCEPTED} and, ordinarily, for
 *                     {@link Status#IDEMPOTENT} — but {@code null} for an {@link Status#IDEMPOTENT}
 *                     result whose original item was since replaced or withdrawn (the
 *                     {@code clientToken} is still recognised as seen, but no current item carries
 *                     it); always {@code null} for {@link Status#REJECTED}
 * @param version      the slot version for an accepted submission/replacement; {@code 0} for
 *                     {@link Status#REJECTED} and for the replaced/withdrawn-item
 *                     {@link Status#IDEMPOTENT} case above
 * @param reason       rejection reason for {@link Status#REJECTED}; {@code null} otherwise
 */
public record SubmissionResult(Status status, String submissionId, int version, String reason) {

  /**
   * Classification of a submit/replace outcome.
   */
  public enum Status {
    /**
     * A new item (or new version) was stored.
     */
    ACCEPTED,
    /**
     * A repeated {@code clientToken} returned the existing item; nothing was stored or emitted.
     */
    IDEMPOTENT,
    /**
     * A configured constraint refused the submission; a {@code COLLECTION_ITEM_REJECTED} event was emitted.
     */
    REJECTED
  }

  /**
   * @return {@code true} when the submission was accepted or idempotently de-duplicated
   */
  public boolean accepted() {
    return status == Status.ACCEPTED || status == Status.IDEMPOTENT;
  }
}
