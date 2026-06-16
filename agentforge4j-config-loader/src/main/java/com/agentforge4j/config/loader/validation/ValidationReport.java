// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import java.util.List;

/**
 * Aggregate result returned by workflow draft validation.
 *
 * @param errors validation errors, empty when the report is valid
 */
public record ValidationReport(
    List<ValidationError> errors
) {

  public ValidationReport {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  /**
   * Returns whether no validation errors are present.
   */
  public boolean isValid() {
    return errors.isEmpty();
  }
}
