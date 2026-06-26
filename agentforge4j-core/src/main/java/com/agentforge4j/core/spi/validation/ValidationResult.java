// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.validation;

import com.agentforge4j.util.Validate;

/**
 * Outcome of an {@link ArtifactValidator}: either valid, or invalid with a non-blank reason. The reason is surfaced in
 * the run's failure/audit state when validation fails the run closed.
 *
 * @param valid   whether the validated artifacts are acceptable
 * @param message failure reason when {@code valid} is {@code false}; {@code null} when valid
 */
public record ValidationResult(boolean valid, String message) {

  /**
   * A passing result.
   *
   * @return a valid result with no message
   */
  public static ValidationResult ok() {
    return new ValidationResult(true, null);
  }

  /**
   * A failing result.
   *
   * @param message non-blank failure reason
   *
   * @return an invalid result carrying the reason
   */
  public static ValidationResult invalid(String message) {
    return new ValidationResult(false, Validate.notBlank(message, "ValidationResult message must not be blank"));
  }
}
