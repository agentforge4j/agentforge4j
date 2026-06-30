// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * Whether a closed collection gate may be reopened.
 */
public enum ReopenPolicy {
  /**
   * A closed gate cannot be reopened; close auto-advances the run.
   */
  NONE,
  /**
   * A closed gate may be reopened while the run still waits at the gate; close holds the run until an
   * explicit continuation advances it, leaving a window in which a reopen is possible.
   */
  ALLOWED
}
