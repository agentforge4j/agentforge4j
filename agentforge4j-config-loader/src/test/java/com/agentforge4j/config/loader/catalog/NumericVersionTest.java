// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NumericVersionTest {

  @Test
  void snapshotQualifierIsIgnored() {
    // A1: 0.0.1-SNAPSHOT must compare equal to 0.0.1 (qualifier stripped, numeric-only).
    assertThat(NumericVersion.compare("0.0.1-SNAPSHOT", "0.0.1")).isZero();
  }

  @Test
  void buildMetadataIsIgnored() {
    assertThat(NumericVersion.compare("1.2.3+build.7", "1.2.3")).isZero();
  }

  @Test
  void ordersByMajorMinorPatch() {
    assertThat(NumericVersion.compare("1.0.0", "0.9.9")).isPositive();
    assertThat(NumericVersion.compare("1.2.0", "1.2.3")).isNegative();
    assertThat(NumericVersion.compare("2.0.0", "2.0.0")).isZero();
  }

  @Test
  void missingComponentsTreatedAsZero() {
    assertThat(NumericVersion.compare("1", "1.0.0")).isZero();
    assertThat(NumericVersion.compare("1.2", "1.2.0")).isZero();
  }

  @Test
  void moreThanThreeComponentsIsRejected() {
    // Fail-closed: a fourth numeric component is malformed, not silently truncated to 1.2.3.
    assertThatThrownBy(() -> NumericVersion.compare("1.2.3.4", "1.2.3"))
        .isInstanceOf(CatalogCompatibilityException.class)
        .hasMessageContaining("more than three");
  }
}
