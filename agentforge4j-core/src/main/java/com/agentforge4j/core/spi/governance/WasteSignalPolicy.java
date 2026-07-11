// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.governance;

/**
 * Reacts to a {@link TokenGovernanceSignal}, called after the runtime has already recorded it as a
 * {@code TOKEN_GOVERNANCE_SIGNAL} audit event (the event is recorded regardless of this policy).
 * Pure reaction seam: OSS ships only the no-op default ({@link #NO_OP}) — legitimately identical
 * retries exist, so acting on a signal (for example blocking) here would be token limiting through
 * the back door, which this design does not do.
 *
 * <p>Raised by the runtime's waste-signal evaluator ({@code com.agentforge4j.runtime.waste.WasteDetector})
 * from {@code AgentInvoker} and {@code AbstractLoopStrategy}; not yet configurable from the
 * bootstrap builder in this OSS runtime version — an embedder wiring the runtime directly (not via
 * {@code AgentForge4jBootstrap}) can still supply a custom implementation to
 * {@code AgentInvoker.Builder#wasteSignalPolicy}.
 */
@FunctionalInterface
public interface WasteSignalPolicy {

  /**
   * No-op default: nothing happens. The shipped bootstrap always uses this.
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
