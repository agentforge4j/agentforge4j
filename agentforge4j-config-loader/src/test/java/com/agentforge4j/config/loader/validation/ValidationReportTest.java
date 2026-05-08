package com.agentforge4j.config.loader.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationReportTest {

  @Test
  void isValid_trueWhenNoErrors() {
    assertThat(new ValidationReport(List.of()).isValid()).isTrue();
  }

  @Test
  void isValid_falseWhenErrorsPresent() {
    ValidationReport report =
        new ValidationReport(List.of(new ValidationError("code", "message")));

    assertThat(report.isValid()).isFalse();
  }

  @Test
  void constructor_normalizesNullErrorsToEmptyList() {
    ValidationReport report = new ValidationReport(null);

    assertThat(report.errors()).isEmpty();
    assertThat(report.isValid()).isTrue();
  }

  @Test
  void constructor_defensivelyCopiesErrorsList() {
    List<ValidationError> mutable = new java.util.ArrayList<>();
    mutable.add(new ValidationError("a", "b"));
    ValidationReport report = new ValidationReport(mutable);
    mutable.clear();

    assertThat(report.errors()).hasSize(1);
  }

  @Test
  void validationError_rejectsBlankCodeOrMessage() {
    assertThatThrownBy(() -> new ValidationError(" ", "msg"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ValidationError("c", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
