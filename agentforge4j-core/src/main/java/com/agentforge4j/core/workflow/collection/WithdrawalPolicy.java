// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * Whether and by whom an existing collection item may be withdrawn.
 */
public enum WithdrawalPolicy {
  /**
   * Withdrawal is not permitted.
   */
  NONE,
  /**
   * Only the actor who submitted an item may withdraw it.
   */
  OWNER_WITHDRAW,
  /**
   * Withdrawal always requires authorization, resolved per attempt: the submitting actor's own
   * withdrawal still requires a {@code withdraw_own} authorization, and any other actor's
   * withdrawal requires {@code withdraw_any}. This is distinct from {@link #OWNER_WITHDRAW}, where
   * the submitter needs no authorization at all.
   */
  AUTHORIZED_WITHDRAW
}
