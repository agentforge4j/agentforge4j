// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

/**
 * Deterministic strategy by which an agent-emitted ledger delta is merged into the ledger section of
 * workflow state. No LLM participates in the merge.
 */
public enum LedgerMergeStrategy {

  /**
   * Replace the entire ledger section with the delta.
   */
  REPLACE_SECTION,

  /**
   * Append the delta's entries to the existing section.
   */
  APPEND,

  /**
   * Merge entries by a key field: a delta entry replaces the existing entry with the same key, and is
   * added otherwise.
   */
  MERGE_BY_KEY
}
