// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextSelectorTest {

  @Test
  void nullVariantDefaultsToFull() {
    ContextSelector selector = new ContextSelector(ContextSourceKind.LEDGER_SECTION,
        "requirements.entries", null);

    assertThat(selector.variant()).isEqualTo(ContextVariant.FULL);
  }

  @Test
  void retainsExplicitVariant() {
    ContextSelector selector = new ContextSelector(ContextSourceKind.CONTEXT_PACK, "coding-standards",
        ContextVariant.COMPACT_ONLY);

    assertThat(selector.variant()).isEqualTo(ContextVariant.COMPACT_ONLY);
  }

  @Test
  void rejectsNullKind() {
    assertThatThrownBy(() -> new ContextSelector(null, "ref", ContextVariant.FULL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankRef() {
    assertThatThrownBy(() -> new ContextSelector(ContextSourceKind.STATE_KEY, " ", ContextVariant.FULL))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
