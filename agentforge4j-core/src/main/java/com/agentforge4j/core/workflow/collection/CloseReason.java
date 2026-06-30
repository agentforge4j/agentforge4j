// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * Why a collection gate was closed, recorded on {@link CollectionState} and in the close audit event.
 */
public enum CloseReason {
  /**
   * Closed by an explicit human close request.
   */
  MANUAL,
  /**
   * Closed because an external deadline elapsed. The deadline timestamp lives
   * outside {@code core}; {@code core} only records that the close carried this provenance.
   */
  DEADLINE,
  /**
   * Closed by an authorized override that bypassed an unmet constraint (for example a minimum-item
   * count). The unmet constraint is recorded in the close audit event.
   */
  OVERRIDE
}
