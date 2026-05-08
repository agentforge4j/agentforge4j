package com.agentforge4j.config.loader.validation;

import com.agentforge4j.util.Validate;

/**
 * Single validation failure item produced by draft validation.
 *
 * @param code    stable identifier for the validation check that failed
 * @param message human-readable error message
 */
public record ValidationError(
    String code,
    String message
) {

  public ValidationError {
    Validate.notBlank(code, "ValidationError code must not be blank");
    Validate.notBlank(message, "ValidationError message must not be blank");
  }
}
