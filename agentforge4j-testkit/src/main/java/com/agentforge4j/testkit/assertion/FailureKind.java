// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.assertion;

/**
 * The discriminator of a run failure, mirroring the {@code kind} of
 * {@code com.agentforge4j.core.workflow.state.RunFailure}.
 */
public enum FailureKind {

  /** An exception propagated out of a step. */
  EXCEPTION,

  /** A human rejected a {@code HUMAN_APPROVAL} gate. */
  STEP_REJECTION
}
