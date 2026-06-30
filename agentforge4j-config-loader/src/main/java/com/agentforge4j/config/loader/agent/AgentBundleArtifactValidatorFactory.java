// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link ArtifactValidatorFactory} for the built-in {@code agent-bundle} validator, discovered via
 * {@link java.util.ServiceLoader}. Produces an {@link AgentBundleArtifactValidator} that runs the production
 * {@link AgentDefinitionAssembler} parse/validate path over the supplied {@link ObjectMapper}.
 */
public final class AgentBundleArtifactValidatorFactory implements ArtifactValidatorFactory {

  /**
   * Creates the agent-bundle validator bound to the given mapper.
   *
   * @param objectMapper the configured mapper; must not be {@code null}
   *
   * @return the agent-bundle validator
   */
  @Override
  public ArtifactValidator create(ObjectMapper objectMapper) {
    return new AgentBundleArtifactValidator(objectMapper);
  }
}
