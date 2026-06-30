// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * Lifecycle phase of a collection gate held in {@link CollectionState}.
 */
public enum CollectionPhase {
  /**
   * Intake is open; submissions, replacements, and withdrawals are accepted.
   */
  OPEN,
  /**
   * Intake is closed; no further submissions are accepted. A closed gate may reopen only when the
   * behaviour's reopen policy allows it.
   */
  CLOSED
}
