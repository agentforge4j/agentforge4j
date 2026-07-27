// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.spi.validation.ArtifactValidationContext;
import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import java.util.Map;

/**
 * {@link ArtifactValidator} that performs no format-specific parsing: it asserts every artifact the selecting
 * {@code VALIDATE} step captured carries non-blank content. The runtime's generic required-file allowlist already
 * guarantees every declared path is present before this validator runs, so this is the generic presence gate a
 * {@code VALIDATE} step selects when it needs nothing beyond that.
 */
public final class RequiredArtifactsPresentValidator implements ArtifactValidator {

  /**
   * Stable id a {@code VALIDATE} step uses to select this validator.
   */
  public static final String VALIDATOR_ID = "required-artifacts-present";

  @Override
  public String validatorId() {
    return VALIDATOR_ID;
  }

  @Override
  public ValidationResult validate(ArtifactValidationContext context) {
    Map<String, String> artifacts = context.artifacts();
    for (Map.Entry<String, String> entry : artifacts.entrySet()) {
      String content = entry.getValue();
      if (content == null || content.isBlank()) {
        return ValidationResult.invalid(
            "required artifact '%s' has no content".formatted(entry.getKey()));
      }
    }
    return ValidationResult.ok();
  }
}
