// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.governance;

/**
 * Reacts to a {@link TokenGovernanceSignal} intended to be raised once the runtime has recorded it
 * as a {@code TOKEN_GOVERNANCE_SIGNAL} audit event. Pure reaction seam: OSS ships only the no-op
 * default ({@link #NO_OP}) — legitimately identical retries exist, so acting on a signal (for
 * example blocking) here would be token limiting through the back door, which this design does not
 * do.
 *
 * <p>The runtime's waste-signal evaluator has no production caller in this runtime version, so no
 * {@code TOKEN_GOVERNANCE_SIGNAL} event is emitted and this SPI is not currently invoked.
 */
@FunctionalInterface
public interface WasteSignalPolicy {

  /**
   * No-op default: nothing happens. Not currently invoked — see the class-level note.
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
