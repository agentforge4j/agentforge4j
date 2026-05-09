package com.agentforge4j.integrations;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
  void validateIsNoOp() {
    StandardIntegrationConfig config = new StandardIntegrationConfig(true, List.of("x"));

    assertThatCode(config::validate).doesNotThrowAnyException();
  }

  @Test
  void disabledWithNullAllowedOperationsRetainsNull() {
    StandardIntegrationConfig config = new StandardIntegrationConfig(false, null);

    assertThat(config.enabled()).isFalse();
    assertThat(config.allowedOperations()).isNull();
  }

  @Test
  void disabledDoesNotCopyListReferenceSemantics() {
    List<String> original = new ArrayList<>(List.of("read"));
    StandardIntegrationConfig config = new StandardIntegrationConfig(false, original);
    original.add("write");

    assertThat(config.allowedOperations()).containsExactly("read", "write");
  }
}
