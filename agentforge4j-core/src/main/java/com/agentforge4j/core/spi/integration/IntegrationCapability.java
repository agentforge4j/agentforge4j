package com.agentforge4j.core.spi.integration;

import com.agentforge4j.util.Validate;

/**
 * A single capability an integration exposes, mapping a logical capability id to the remote tool
 * that fulfils it.
 *
 * @param capability     logical capability id, lowercase {@code <domain>.<verb_object>}; non-blank
 * @param remoteToolName the integration's advertised tool name for this capability, or {@code null}
 *                       for the HTTP tier (where the capability is fulfilled by a code-defined
 *                       endpoint rather than a named remote tool)
 * @param mutating       whether invoking the capability mutates remote state (a hint to gate it
 *                       with an approval policy)
 */
public record IntegrationCapability(String capability, String remoteToolName, boolean mutating) {

  /**
   * Validates that {@code capability} is non-blank.
   */
  public IntegrationCapability {
    Validate.notBlank(capability, "IntegrationCapability capability must not be blank");
  }
}
