package com.agentforge4j.integrations;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardIntegrationConfigTest {

  @Test
  void enabledTrueWithNullAllowedOperationsBecomesEmptyList() {
    StandardIntegrationConfig config = new StandardIntegrationConfig(true, null);

    assertThat(config.enabled()).isTrue();
    assertThat(config.allowedOperations()).isEmpty();
  }

  @Test
  void enabledTrueCopiesAllowedOperations() {
    List<String> original = new ArrayList<>(List.of("read"));
    StandardIntegrationConfig config = new StandardIntegrationConfig(true, original);
    original.add("write");

    assertThat(config.allowedOperations()).containsExactly("read");
  }

  @Test
  void validateRejectsBlankAllowedOperation() {
    assertThatThrownBy(() -> new StandardIntegrationConfig(true, List.of("x", " ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null or blank");
  }

  @Test
  void validateRejectsNullAllowedOperation() {
    List<String> withNull = new ArrayList<>();
    withNull.add("read");
    withNull.add(null);

    assertThatThrownBy(() -> new StandardIntegrationConfig(true, withNull))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null or blank");
  }

  @Test
  void disabledWithNullAllowedOperationsBecomesEmptyList() {
    StandardIntegrationConfig config = new StandardIntegrationConfig(false, null);

    assertThat(config.enabled()).isFalse();
    assertThat(config.allowedOperations()).isEmpty();
  }

  @Test
  void disabledCopiesAllowedOperations() {
    List<String> original = new ArrayList<>(List.of("read"));
    StandardIntegrationConfig config = new StandardIntegrationConfig(false, original);
    original.add("write");

    assertThat(config.allowedOperations()).containsExactly("read");
  }
}
