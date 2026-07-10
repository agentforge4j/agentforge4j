// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.scenario;

import com.agentforge4j.core.workflow.collection.CloseReason;
import com.agentforge4j.util.Validate;

/**
 * One operation in a scripted collection-gate interaction (see {@link GateResponse.Collection}). A
 * single {@code Collection} response carries an ordered list of these and the harness applies them at
 * one {@code AWAITING_COLLECTION} pause: zero or more submits/replaces/withdraws followed by a close.
 *
 * <p>Because runtime {@code submissionId}s are non-deterministic (runtime-assigned), {@link Replace}
 * and {@link Withdraw} target a prior submit by its 0-based ordinal within this op list rather than by
 * a literal id; the harness maps that ordinal to the real id it captured when applying the submit.
 */
public sealed interface CollectionOp
    permits CollectionOp.Submit, CollectionOp.Replace, CollectionOp.Withdraw, CollectionOp.Close {

  /**
   * Submits a new item. {@code payload} is the item's inline JSON (the scenario {@code payloadRef});
   * {@code clientToken}/{@code dedupeKey} are optional idempotency/dedupe keys.
   *
   * @param payload     inline JSON payload; may be {@code null} for an empty submission
   * @param clientToken optional idempotency token
   * @param dedupeKey   optional dedupe key
   * @param actorId     the submitting actor; {@code null} defaults to the harness's actor, so a
   *                    scenario can script a multi-submitter collection or a specific submitter to
   *                    later exercise owner-scoped replace/withdraw
   */
  record Submit(String payload, String clientToken, String dedupeKey, String actorId)
      implements CollectionOp {

    /**
     * Validates the actor id (only when an explicit one is given; {@code null} means the harness
     * default).
     *
     * @param payload     inline JSON payload; may be {@code null}
     * @param clientToken optional idempotency token; may be {@code null}
     * @param dedupeKey   optional dedupe key; may be {@code null}
     * @param actorId     the submitting actor, or {@code null} to default to the harness's actor
     */
    public Submit {
      if (actorId != null) {
        Validate.notBlank(actorId, "Submit actorId must not be blank when provided");
      }
    }
  }

  /**
   * Replaces the item created by the {@code target}-th submit in this op list.
   *
   * @param target  0-based ordinal of the originating submit; must not be negative
   * @param payload replacement inline JSON payload; may be {@code null}
   * @param actorId the replacing actor; {@code null} defaults to the harness's actor. Under an
   *                owner-scoped replacement policy this must match the target's submitting actor
   *                (a scripted mismatch is a scenario asserting the runtime's denial)
   */
  record Replace(int target, String payload, String actorId) implements CollectionOp {

    /**
     * Validates the target ordinal and the actor id.
     *
     * @param target  0-based ordinal of the originating submit; must not be negative
     * @param payload replacement inline JSON payload; may be {@code null}
     * @param actorId the replacing actor, or {@code null} to default to the harness's actor
     */
    public Replace {
      Validate.isNotNegative(target, "Replace target must not be negative");
      if (actorId != null) {
        Validate.notBlank(actorId, "Replace actorId must not be blank when provided");
      }
    }
  }

  /**
   * Withdraws the item created by the {@code target}-th submit in this op list.
   *
   * @param target  0-based ordinal of the originating submit; must not be negative
   * @param actorId the withdrawing actor; {@code null} defaults to the harness's actor. Under an
   *                owner-scoped withdrawal policy this must match the target's submitting actor
   *                (a scripted mismatch is a scenario asserting the runtime's denial)
   */
  record Withdraw(int target, String actorId) implements CollectionOp {

    /**
     * Validates the target ordinal and the actor id.
     *
     * @param target  0-based ordinal of the originating submit; must not be negative
     * @param actorId the withdrawing actor, or {@code null} to default to the harness's actor
     */
    public Withdraw {
      Validate.isNotNegative(target, "Withdraw target must not be negative");
      if (actorId != null) {
        Validate.notBlank(actorId, "Withdraw actorId must not be blank when provided");
      }
    }
  }

  /**
   * Closes the gate.
   *
   * @param reason   the close reason; must not be {@code null} and must not be
   *                 {@link CloseReason#OVERRIDE} — that value is a derived outcome
   *                 {@code CloseRequest} rejects as a requestable reason; use {@code override} instead
   * @param override whether to close despite an unmet minimum
   */
  record Close(CloseReason reason, boolean override) implements CollectionOp {

    public Close {
      Validate.notNull(reason, "Close reason must not be null");
      Validate.isTrue(reason != CloseReason.OVERRIDE,
          "Close reason must not be OVERRIDE; it is a derived outcome, not a requestable reason");
    }
  }
}
