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

  @Test
  void compactKeyIsNamespacedUnderReservedPrefix() {
    assertThat(ReservedContextKeys.compactKey("LEDGER_SECTION:requirements"))
        .isEqualTo("__compact.LEDGER_SECTION:requirements")
        .startsWith(ReservedContextKeys.COMPACT_KEY_PREFIX);
  }

  @Test
  void compactKeyRejectsBlankSourceId() {
    assertThatThrownBy(() -> ReservedContextKeys.compactKey(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void grantedKeyIsNamespacedUnderReservedPrefix() {
    assertThat(ReservedContextKeys.grantedKey("STATE_KEY:design.md"))
        .isEqualTo("__granted.STATE_KEY:design.md")
        .startsWith(ReservedContextKeys.GRANTED_KEY_PREFIX);
  }

  @Test
  void grantedKeyRejectsBlankSourceId() {
    assertThatThrownBy(() -> ReservedContextKeys.grantedKey(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void expansionCountKeyIsNamespacedUnderReservedPrefix() {
    assertThat(ReservedContextKeys.expansionCountKey(7))
        .isEqualTo("__expansionCount.7")
        .startsWith(ReservedContextKeys.EXPANSION_COUNT_KEY_PREFIX);
  }

  @Test
  void llmSummaryInputKeyIsNamespacedUnderReservedPrefix() {
    assertThat(ReservedContextKeys.llmSummaryInputKey("LEDGER_SECTION:requirements"))
        .isEqualTo("__compactSourceInput.LEDGER_SECTION:requirements")
        .startsWith(ReservedContextKeys.LLM_SUMMARY_INPUT_KEY_PREFIX);
  }

  @Test
  void llmSummaryInputKeyRejectsBlankSourceId() {
    assertThatThrownBy(() -> ReservedContextKeys.llmSummaryInputKey(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
