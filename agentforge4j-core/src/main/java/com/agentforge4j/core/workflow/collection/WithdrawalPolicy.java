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
   * Any actor authorized for the {@code withdraw_any} action may withdraw an item.
   */
  AUTHORIZED_WITHDRAW
}
