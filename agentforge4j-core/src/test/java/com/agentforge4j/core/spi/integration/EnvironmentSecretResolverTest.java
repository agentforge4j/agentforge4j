// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentSecretResolverTest {

  private static final String REFERENCE = "AGENTFORGE4J_TEST_SECRET_REFERENCE";

  private final EnvironmentSecretResolver resolver = new EnvironmentSecretResolver();

  @AfterEach
  void clearProperty() {
    System.clearProperty(REFERENCE);
  }

  @Test
  void resolvesFromSystemPropertyWhenTheEnvironmentVariableIsUnset() {
    System.setProperty(REFERENCE, "s3cr3t-value");

    assertThat(resolver.resolve(REFERENCE)).isEqualTo("s3cr3t-value");
  }

  @Test
  void failsFastNamingTheReferenceButNotTheValueWhenUnresolved() {
    assertThatThrownBy(() -> resolver.resolve(REFERENCE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(REFERENCE);
  }

  @Test
  void failsFastWhenTheSystemPropertyIsBlank() {
    System.setProperty(REFERENCE, "   ");

    assertThatThrownBy(() -> resolver.resolve(REFERENCE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(REFERENCE);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejectsABlankReference(String reference) {
    assertThatThrownBy(() -> resolver.resolve(reference))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
