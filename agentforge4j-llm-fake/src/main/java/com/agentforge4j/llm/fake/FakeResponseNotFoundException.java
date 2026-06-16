// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.llm.api.LlmInvocationException;

/**
 * Thrown when the fake provider cannot resolve a scripted response: the request carried no invocation identity, no
 * script is registered for the run, or the run's script has no entry for the resolved key. Fail-closed — the fake never
 * fabricates a default response.
 *
 * <p>Extends {@link LlmInvocationException} so it is a proper {@code LlmClient} failure, and carries
 * no HTTP status, so {@code RetryingLlmClient} classifies it as non-transient and does not retry the miss (a retry
 * would both mask the script gap and advance the ordinal counter again).
 */
public final class FakeResponseNotFoundException extends LlmInvocationException {

  /**
   * Creates a new exception with the given message.
   *
   * @param message a description naming exactly what could not be resolved
   */
  public FakeResponseNotFoundException(String message) {
    super(message);
  }
}
