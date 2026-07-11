// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * Whether and by whom an existing collection item may be replaced with a new version.
 */
public enum ReplacementPolicy {
  /**
   * Replacement is not permitted.
   */
  NONE,
  /**
   * Only the actor who submitted an item may replace it.
   */
  OWNER_REPLACE,
  /**
   * Replacement always requires authorization, resolved per attempt: the submitting actor's own
   * replace still requires a {@code replace_own} authorization, and any other actor's replace
   * requires {@code replace_any}. This is distinct from {@link #OWNER_REPLACE}, where the
   * submitter needs no authorization at all.
   */
  AUTHORIZED_REPLACE
}
