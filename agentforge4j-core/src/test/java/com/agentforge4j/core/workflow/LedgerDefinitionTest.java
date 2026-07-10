// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerDefinitionTest {

  @Test
  void mergeByKeyRequiresKeyField() {
    LedgerDefinition ledger = new LedgerDefinition("requirements", "schema/req.json",
        LedgerMergeStrategy.MERGE_BY_KEY, "id");

    assertThat(ledger.mergeKeyField()).isEqualTo("id");
  }

  @Test
  void appendAllowsNullKeyField() {
    LedgerDefinition ledger = new LedgerDefinition("decisions", "schema/dec.json",
        LedgerMergeStrategy.APPEND, null);

    assertThat(ledger.mergeStrategy()).isEqualTo(LedgerMergeStrategy.APPEND);
    assertThat(ledger.mergeKeyField()).isNull();
  }

  @Test
  void mergeByKeyRejectsBlankKeyField() {
    assertThatThrownBy(() -> new LedgerDefinition("r", "s", LedgerMergeStrategy.MERGE_BY_KEY, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonMergeByKeyRejectsPresentKeyField() {
    assertThatThrownBy(
        () -> new LedgerDefinition("r", "s", LedgerMergeStrategy.REPLACE_SECTION, "id"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankIdAndSchemaRef() {
    assertThatThrownBy(() -> new LedgerDefinition(" ", "s", LedgerMergeStrategy.APPEND, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LedgerDefinition("r", " ", LedgerMergeStrategy.APPEND, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
