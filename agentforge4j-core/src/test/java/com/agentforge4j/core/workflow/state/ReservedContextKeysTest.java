// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservedContextKeysTest {

  @Test
  void ledgerKeyIsNamespacedUnderReservedPrefix() {
    assertThat(ReservedContextKeys.ledgerKey("requirements"))
        .isEqualTo("__ledger.requirements")
        .startsWith(ReservedContextKeys.LEDGER_KEY_PREFIX);
  }

  @Test
  void ledgerKeyRejectsBlankId() {
    assertThatThrownBy(() -> ReservedContextKeys.ledgerKey(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
