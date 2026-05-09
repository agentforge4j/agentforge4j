package com.agentforge4j.integrations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpIntegrationRegistryTest {

  @Test
  void resolveIsAlwaysEmpty() {
    assertThat(NoOpIntegrationRegistry.INSTANCE.resolve("any-id")).isEmpty();
  }

  @Test
  void resolveReturnsEmptyForNullId() {
    assertThat(NoOpIntegrationRegistry.INSTANCE.resolve(null)).isEmpty();
  }

  @Test
  void operationsNeverAllowed() {
    assertThat(NoOpIntegrationRegistry.INSTANCE.isOperationAllowed("x", "op")).isFalse();
  }

  @Test
  void operationsNeverAllowedForNullArguments() {
    assertThat(NoOpIntegrationRegistry.INSTANCE.isOperationAllowed(null, null)).isFalse();
  }

  @Test
  void neverEnabled() {
    assertThat(NoOpIntegrationRegistry.INSTANCE.isEnabled("x")).isFalse();
  }

  @Test
  void neverEnabledForNullId() {
    assertThat(NoOpIntegrationRegistry.INSTANCE.isEnabled(null)).isFalse();
  }

  @Test
  void instanceIsSingleton() {
    assertThat(NoOpIntegrationRegistry.INSTANCE).isSameAs(NoOpIntegrationRegistry.INSTANCE);
  }
}
