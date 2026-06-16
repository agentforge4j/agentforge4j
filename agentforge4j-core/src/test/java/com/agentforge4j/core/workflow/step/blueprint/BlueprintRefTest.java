// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.blueprint;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintRefTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n"})
  void rejects_blank_blueprint_id(String id) {
    assertThatThrownBy(() -> new BlueprintRef(id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blueprintId");
  }
}
