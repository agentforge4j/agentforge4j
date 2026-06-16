// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

/**
 * Thrown when a capability cannot be resolved to exactly one provider — either none fulfils it or
 * more than one does (ambiguous). Resolvers must never resolve ambiguity by silent first-wins.
 */
public class CapabilityResolutionException extends RuntimeException {

  /**
   * Creates the exception with a detail message.
   *
   * @param message the detail message, naming the conflicting providers when ambiguous
   */
  public CapabilityResolutionException(String message) {
    super(message);
  }
}
