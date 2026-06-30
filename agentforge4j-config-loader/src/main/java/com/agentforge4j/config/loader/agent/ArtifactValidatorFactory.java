// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory that produces an {@link ArtifactValidator} configured with the supplied {@link ObjectMapper}. Built-in
 * factories are discovered via {@link java.util.ServiceLoader} and their validators registered in the runtime's
 * validator registry.
 *
 * <p>The factory indirection (rather than a no-argument {@code ArtifactValidator} constructor) is required because a
 * validator must parse artifacts with the same configured {@code ObjectMapper} the agent loaders use, so validation
 * stays in lockstep with production loading; a {@code ServiceLoader}-instantiated no-arg validator would silently fall
 * back to a default mapper.
 */
public interface ArtifactValidatorFactory {

  /**
   * Creates a validator bound to the given mapper.
   *
   * @param objectMapper the configured mapper the produced validator must parse with; must not be {@code null}
   *
   * @return the validator instance
   */
  ArtifactValidator create(ObjectMapper objectMapper);
}
