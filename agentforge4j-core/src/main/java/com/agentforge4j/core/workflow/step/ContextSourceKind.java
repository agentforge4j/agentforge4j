// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

/**
 * Kind of source a {@link ContextSelector} points at when a step declares the context it receives.
 */
public enum ContextSourceKind {

  /**
   * A section of a declared ledger (the {@code ref} carries the ledger id and optional section).
   */
  LEDGER_SECTION,

  /**
   * A workflow artifact (the {@code ref} carries the artifact id).
   */
  ARTIFACT,

  /**
   * A named context pack (the {@code ref} carries the pack name).
   */
  CONTEXT_PACK,

  /**
   * A single workflow-state key (the {@code ref} carries the key).
   */
  STATE_KEY,

  /**
   * The output of an earlier step (the {@code ref} carries the step id).
   */
  STEP_OUTPUT
}
