package com.agentforge4j.integrations;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubIntegrationConfigTest {

  @Test
  void nullAllowedOperationsBecomesEmptyList() {
    StubIntegrationConfig config = new StubIntegrationConfig(true, null);

    assertThat(config.allowedOperations()).isEmpty();
  }

  @Test
  void allowedOperationsAreCopied() {
    List<String> original = new ArrayList<>(List.of("read"));
    StubIntegrationConfig config = new StubIntegrationConfig(true, original);
    original.add("write");

    assertThat(config.allowedOperations()).containsExactly("read");
  }

  @Test
  void failingValidateThrowsWhenInvoked() {
    StubIntegrationConfig config = StubIntegrationConfig.failingValidate();

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid config");
  }
}
