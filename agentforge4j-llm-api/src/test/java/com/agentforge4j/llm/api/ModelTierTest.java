// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelTierTest {

  @Test
  void definesExactlyFourTiersInAscendingCapabilityOrder() {
    assertThat(ModelTier.values())
        .containsExactly(ModelTier.LITE, ModelTier.STANDARD, ModelTier.POWERFUL, ModelTier.PREMIUM);
  }

  @Test
  void valueOfRoundTripsTierNames() {
    assertThat(ModelTier.valueOf("LITE")).isEqualTo(ModelTier.LITE);
    assertThat(ModelTier.valueOf("STANDARD")).isEqualTo(ModelTier.STANDARD);
    assertThat(ModelTier.valueOf("POWERFUL")).isEqualTo(ModelTier.POWERFUL);
    assertThat(ModelTier.valueOf("PREMIUM")).isEqualTo(ModelTier.PREMIUM);
  }

  @Test
  void fromNameAcceptsExactUppercase() {
    assertThat(ModelTier.fromName("LITE")).isEqualTo(ModelTier.LITE);
    assertThat(ModelTier.fromName("STANDARD")).isEqualTo(ModelTier.STANDARD);
    assertThat(ModelTier.fromName("POWERFUL")).isEqualTo(ModelTier.POWERFUL);
    assertThat(ModelTier.fromName("PREMIUM")).isEqualTo(ModelTier.PREMIUM);
  }

  @Test
  void fromNameIsCaseInsensitiveAndWhitespaceTolerant() {
    assertThat(ModelTier.fromName("powerful")).isEqualTo(ModelTier.POWERFUL);
    assertThat(ModelTier.fromName("Standard")).isEqualTo(ModelTier.STANDARD);
    assertThat(ModelTier.fromName("  lite  ")).isEqualTo(ModelTier.LITE);
  }

  @Test
  void fromNameRejectsBlank() {
    assertThatThrownBy(() -> ModelTier.fromName("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromNameRejectsUnknownName() {
    assertThatThrownBy(() -> ModelTier.fromName("FAST"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
