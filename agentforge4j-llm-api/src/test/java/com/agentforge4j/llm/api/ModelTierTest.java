package com.agentforge4j.llm.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTierTest {

  @Test
  void definesExactlyThreeTiersInAscendingCapabilityOrder() {
    assertThat(ModelTier.values())
        .containsExactly(ModelTier.LITE, ModelTier.STANDARD, ModelTier.POWERFUL);
  }

  @Test
  void valueOfRoundTripsTierNames() {
    assertThat(ModelTier.valueOf("LITE")).isEqualTo(ModelTier.LITE);
    assertThat(ModelTier.valueOf("STANDARD")).isEqualTo(ModelTier.STANDARD);
    assertThat(ModelTier.valueOf("POWERFUL")).isEqualTo(ModelTier.POWERFUL);
  }
}
