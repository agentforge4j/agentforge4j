// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.validation;

/**
 * SPI for the format-specific validation of a generated artifact bundle, selected by a stable {@link #validatorId()}
 * that a {@code VALIDATE} step names. Implementations parse and semantically validate the captured artifacts (for
 * example, asserting that an emitted {@code agent.json} loads as a valid agent definition); the generic required-file
 * allowlist, path-safety, and context-equality rules are enforced by the runtime around the validator, not inside it.
 *
 * <p>Implementations must be deterministic and side-effect free. A {@code null} or
 * {@link ValidationResult#invalid(String) invalid} result fails the run closed.
 */
public interface ArtifactValidator {

  /**
   * The stable identifier a {@code VALIDATE} step uses to select this validator.
   *
   * @return non-blank validator id
   */
  String validatorId();

  /**
   * Validates the captured artifacts.
   *
   * @param context read-only view of the selecting step's declared artifacts
   *
   * @return the validation outcome
   */
  ValidationResult validate(ArtifactValidationContext context);
}
