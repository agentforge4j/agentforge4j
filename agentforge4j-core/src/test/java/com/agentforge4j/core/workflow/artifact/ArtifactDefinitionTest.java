// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.artifact;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactDefinitionTest {

  private static final ArtifactItem ITEM = new TextArtifactItem("q1", "Question", false, null);

  @Test
  void rejects_blank_id() {
    assertThatThrownBy(() -> new ArtifactDefinition(" ", List.of(ITEM)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void rejects_empty_items() {
    assertThatThrownBy(() -> new ArtifactDefinition("art", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void rejects_reserved_namespace_id() {
    // Submitted answers land in context under <id>.<answerKey>; a reserved id would place every
    // answer inside the runtime-owned __ namespace that rewind sweeps deliberately preserve.
    assertThatThrownBy(() -> new ArtifactDefinition("__form", List.of(ITEM)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved")
        .hasMessageContaining("__form");
  }

  @Test
  void allows_non_reserved_underscore_ids() {
    assertThat(new ArtifactDefinition("_form", List.of(ITEM)).id()).isEqualTo("_form");
    assertThat(new ArtifactDefinition("a__b", List.of(ITEM)).id()).isEqualTo("a__b");
  }

  @Test
  void items_are_copied() {
    ArtifactDefinition def = new ArtifactDefinition("art", List.of(ITEM));
    assertThatThrownBy(() -> def.items().add(ITEM))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
