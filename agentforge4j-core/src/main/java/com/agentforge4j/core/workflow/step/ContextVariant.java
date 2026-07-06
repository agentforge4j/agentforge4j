// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

/**
 * Which form of a selected context source a step receives: the full source or a compact sibling.
 */
public enum ContextVariant {

  /**
   * Always use the full source.
   */
  FULL,

  /**
   * Use the compact sibling when one exists and its fingerprint matches the current source; otherwise
   * fall back to the full source.
   */
  COMPACT_PREFERRED,

  /**
   * Use the compact sibling only. An absent or stale compact sibling is a controlled failure with no
   * fallback to the full source.
   */
  COMPACT_ONLY
}
