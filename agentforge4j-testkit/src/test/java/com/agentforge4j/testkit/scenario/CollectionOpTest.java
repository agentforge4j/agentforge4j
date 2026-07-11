// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.scenario;

import com.agentforge4j.core.workflow.collection.CloseReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionOpTest {

  @Test
  void closeRejectsAnOverrideReasonAtConstruction() {
    assertThatThrownBy(() -> new CollectionOp.Close(CloseReason.OVERRIDE, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("OVERRIDE");
  }
}
