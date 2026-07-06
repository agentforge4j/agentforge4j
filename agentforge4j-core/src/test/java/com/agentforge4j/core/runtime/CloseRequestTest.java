// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.runtime;

import com.agentforge4j.core.workflow.collection.CloseReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloseRequestTest {

  @Test
  void acceptsManualAndDeadlineReasons() {
    CloseRequest manual = new CloseRequest("actor-1", CloseReason.MANUAL, false, null);
    assertThat(manual.reason()).isEqualTo(CloseReason.MANUAL);

    CloseRequest deadline = new CloseRequest("actor-1", CloseReason.DEADLINE, false, null);
    assertThat(deadline.reason()).isEqualTo(CloseReason.DEADLINE);
  }

  @Test
  void rejectsOverrideAsARequestedReason() {
    // OVERRIDE is a derived outcome the gate records when the override flag bypasses an unmet
    // constraint -- a caller requesting it directly would otherwise bypass checkClosable's
    // manualClose/externalDeadlineClosable gating entirely, since that check only recognises MANUAL
    // and DEADLINE.
    assertThatThrownBy(() -> new CloseRequest("actor-1", CloseReason.OVERRIDE, true, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
