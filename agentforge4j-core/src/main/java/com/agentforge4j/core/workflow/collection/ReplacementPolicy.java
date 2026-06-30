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
   * Any actor authorized for the {@code replace_any} action may replace an item.
   */
  AUTHORIZED_REPLACE
}
