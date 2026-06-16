// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.spar;

/**
 * Why a SPAR step finished its exchange loop (excluding final resolution).
 */
public enum SparLoopTerminationReason {

  /** Ran all configured rounds while at least one side still had a valid continuation request. */
  MAX_ROUNDS_REACHED,

  /** Neither side requested another round with {@code wantsAnotherRound=true}. */
  EARLY_STOP_BOTH_DONE,

  /**
   * No side satisfied the bar for another round (e.g. blank or vague reason, or only one side
   * tried with an invalid justification).
   */
  EARLY_STOP_NO_VALID_CONTINUATION
}
