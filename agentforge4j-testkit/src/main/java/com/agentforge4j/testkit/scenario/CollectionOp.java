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
   */
  record Submit(String payload, String clientToken, String dedupeKey) implements CollectionOp {
  }

  /**
   * Replaces the item created by the {@code target}-th submit in this op list.
   *
   * @param target  0-based ordinal of the originating submit; must not be negative
   * @param payload replacement inline JSON payload; may be {@code null}
   */
  record Replace(int target, String payload) implements CollectionOp {

    public Replace {
      Validate.isNotNegative(target, "Replace target must not be negative");
    }
  }

  /**
   * Withdraws the item created by the {@code target}-th submit in this op list.
   *
   * @param target 0-based ordinal of the originating submit; must not be negative
   */
  record Withdraw(int target) implements CollectionOp {

    public Withdraw {
      Validate.isNotNegative(target, "Withdraw target must not be negative");
    }
  }

  /**
   * Closes the gate.
   *
   * @param reason   the close reason; must not be {@code null}
   * @param override whether to close despite an unmet minimum
   */
  record Close(CloseReason reason, boolean override) implements CollectionOp {

    public Close {
      Validate.notNull(reason, "Close reason must not be null");
    }
  }
}
