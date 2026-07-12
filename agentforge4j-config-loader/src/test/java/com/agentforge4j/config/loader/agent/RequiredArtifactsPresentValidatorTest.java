// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.core.spi.validation.ArtifactValidationContext;
import com.agentforge4j.core.spi.validation.ValidationResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredArtifactsPresentValidatorTest {

  private final RequiredArtifactsPresentValidator validator = new RequiredArtifactsPresentValidator();

  private static ArtifactValidationContext context(Map<String, String> artifacts) {
    return () -> artifacts;
  }

  @Test
  void validator_id_is_required_artifacts_present() {
    assertThat(validator.validatorId()).isEqualTo(RequiredArtifactsPresentValidator.VALIDATOR_ID);
  }

  @Test
  void all_non_blank_artifact_values_are_valid() {
    ValidationResult result = validator.validate(context(Map.of(
        "notes.txt", "some content",
        "summary.txt", "more content")));

    assertThat(result.valid()).isTrue();
  }

  @Test
  void blank_artifact_value_is_invalid() {
    ValidationResult result = validator.validate(context(Map.of(
        "notes.txt", "some content",
        "summary.txt", "   ")));

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("summary.txt").contains("no content");
  }

  @Test
  void empty_artifact_value_is_invalid() {
    ValidationResult result = validator.validate(context(Map.of("notes.txt", "")));

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("notes.txt");
  }

  @Test
  void no_artifacts_is_valid() {
    ValidationResult result = validator.validate(context(Map.of()));

    assertThat(result.valid()).isTrue();
  }
}
