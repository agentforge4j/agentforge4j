// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrameworkVersionTest {

  @Test
  void current_returnsTheFilteredReactorVersion() {
    String version = FrameworkVersion.current();

    assertThat(version).isNotBlank();
    // The Maven-filtered placeholder must have been substituted at build time.
    assertThat(version).doesNotContain("${");
    assertThat(version).matches("\\d+\\.\\d+\\.\\d+.*");
  }
}
