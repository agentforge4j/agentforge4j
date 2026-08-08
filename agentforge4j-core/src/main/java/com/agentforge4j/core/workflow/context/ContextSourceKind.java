// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

/**
 * The kind of thing a {@link ContextSource} addresses. The kind fixes how the accompanying
 * {@code ref} is to be read, so that two sources are the same source only when both their kind and
 * their reference match.
 *
 * <p>Each constant names a source that the framework already models. The set is deliberately
 * additive: a new kind is introduced together with the source it addresses, never ahead of it.
 */
public enum ContextSourceKind {

  /**
   * A single workflow-state key; the {@code ref} carries the key.
   */
  STATE_KEY,

  /**
   * A workflow artifact; the {@code ref} carries the artifact id.
   */
  ARTIFACT,

  /**
   * The output of an earlier step; the {@code ref} carries the step id.
   */
  STEP_OUTPUT
}
