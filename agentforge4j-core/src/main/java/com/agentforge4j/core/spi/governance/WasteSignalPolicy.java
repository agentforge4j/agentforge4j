// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.governance;

/**
 * Reacts to a {@link TokenGovernanceSignal} the runtime has already recorded as a
 * {@code TOKEN_GOVERNANCE_SIGNAL} audit event. Pure reaction seam: OSS ships only the no-op default
 * ({@link #NO_OP}) since the event is already recorded regardless — legitimately identical retries
 * exist, so acting on a signal (for example blocking) here would be token limiting through the back
 * door, which this design does not do.
 */
@FunctionalInterface
public interface WasteSignalPolicy {

  /**
   * No-op default: the signal has already been recorded as an audit event; nothing further happens.
   */
  WasteSignalPolicy NO_OP = signal -> {
  };

  /**
   * Reacts to a raised signal.
   *
   * @param signal the signal that was raised; never {@code null}
   */
  void onSignal(TokenGovernanceSignal signal);
}
