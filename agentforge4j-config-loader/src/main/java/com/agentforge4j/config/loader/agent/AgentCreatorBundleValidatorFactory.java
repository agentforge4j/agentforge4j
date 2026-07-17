// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link ArtifactValidatorFactory} for the built-in {@code agent-creator-bundle} validator, discovered via
 * {@link java.util.ServiceLoader}. Produces an {@link AgentCreatorBundleValidator} that runs the production
 * agent-bundle parse/validate path plus the verification-starter structural checks over the supplied
 * {@link ObjectMapper}.
 */
public final class AgentCreatorBundleValidatorFactory implements ArtifactValidatorFactory {

  /**
   * Creates the agent-creator-bundle validator bound to the given mapper.
   *
   * @param objectMapper the configured mapper; must not be {@code null}
   *
   * @return the agent-creator-bundle validator
   */
  @Override
  public ArtifactValidator create(ObjectMapper objectMapper) {
    return new AgentCreatorBundleValidator(objectMapper);
  }
}
