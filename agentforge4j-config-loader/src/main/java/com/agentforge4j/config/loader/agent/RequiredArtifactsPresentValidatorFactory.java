// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link ArtifactValidatorFactory} for the built-in {@code required-artifacts-present} validator, discovered via
 * {@link java.util.ServiceLoader}. Produces a {@link RequiredArtifactsPresentValidator}; the validator performs no
 * mapper-dependent parsing, so the supplied {@link ObjectMapper} is unused.
 */
public final class RequiredArtifactsPresentValidatorFactory implements ArtifactValidatorFactory {

  /**
   * Creates the required-artifacts-present validator.
   *
   * @param objectMapper the configured mapper; unused by this validator
   *
   * @return the required-artifacts-present validator
   */
  @Override
  public ArtifactValidator create(ObjectMapper objectMapper) {
    return new RequiredArtifactsPresentValidator();
  }
}
