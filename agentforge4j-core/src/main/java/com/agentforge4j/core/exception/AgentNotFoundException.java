// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.exception;

/**
 * Thrown when an agent with the given id is not found in the agent repository.
 */
public class AgentNotFoundException extends RuntimeException {

  public AgentNotFoundException(String agentId) {
    super("Agent not found: %s".formatted(agentId));
  }
}
