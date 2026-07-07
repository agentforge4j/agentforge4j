// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.contextpack;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextPackTest {

  private static ContextPackVariant variant() {
    return new ContextPackVariant("full", "content", "abc123");
  }

  @Test
  void buildsWithVariantsAndDefaultsTags() {
    ContextPack pack = new ContextPack("p", "1.0.0", null, null, Map.of("full", variant()));

    assertThat(pack.tags()).isEmpty();
    assertThat(pack.variants()).containsOnlyKeys("full");
    assertThat(pack.description()).isNull();
  }

  @Test
  void rejectsBlankNameOrVersion() {
    assertThatThrownBy(() -> new ContextPack(" ", "1.0.0", null, null, Map.of("full", variant())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ContextPack("p", " ", null, null, Map.of("full", variant())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsEmptyVariants() {
    assertThatThrownBy(() -> new ContextPack("p", "1.0.0", null, List.of(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsVariantKeyThatDoesNotMatchVariantName() {
    assertThatThrownBy(() -> new ContextPack("p", "1.0.0", null, null, Map.of("wrong", variant())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wrong")
        .hasMessageContaining("full");
  }

  @Test
  void variantRejectsBlankNameAndFingerprintButAllowsEmptyContent() {
    assertThat(new ContextPackVariant("full", "", "fp").content()).isEmpty();
    assertThatThrownBy(() -> new ContextPackVariant(" ", "c", "fp"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ContextPackVariant("full", "c", " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ContextPackVariant("full", null, "fp"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
