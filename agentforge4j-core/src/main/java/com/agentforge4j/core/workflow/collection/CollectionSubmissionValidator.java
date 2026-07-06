// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * SPI through which the embedding application applies its own policy to collection-gate
 * submissions. The runtime consults this validator on every submit and replace, after the gate's
 * declared constraints (caps, payload size, duplicate policy, item schema) have passed and before
 * the item is stored; a {@link Decision#deny(String) denial} rejects the submission and emits a
 * {@code COLLECTION_ITEM_REJECTED} audit event carrying the denial reason.
 *
 * <p>Typical host policies: stricter client-token rules (formats, replay windows, mapping or
 * normalisation performed before calling the gate), payload content restrictions, or
 * per-deployment file constraints. The framework itself imposes no such policy — the shipped
 * {@link DefaultCollectionSubmissionValidator} guards only runtime integrity.
 *
 * <p>Implementations must be deterministic per input, side-effect free, and safe for concurrent
 * use. They are called while the run's collection lock is held, so they must not call back into
 * the runtime.
 */
public interface CollectionSubmissionValidator {

  /**
   * Decides whether one submission attempt is admitted.
   *
   * @param context read-only view of the attempt; never {@code null}
   *
   * @return the decision; a {@code null} return is treated as a denial (fail closed)
   */
  Decision validate(CollectionSubmissionContext context);
}
