// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the addressing contract: what a source identifies, when two sources are the same source,
 * and which values are refused.
 */
class ContextSourceTest {

  @Test
  void carriesTheKindAndReferenceItWasGiven() {
    ContextSource source = new ContextSource(ContextSourceKind.STATE_KEY, "shared-note");

    assertThat(source.kind()).isEqualTo(ContextSourceKind.STATE_KEY);
    assertThat(source.ref()).isEqualTo("shared-note");
  }

  @Test
  void sameKindAndReferenceIsTheSameSource() {
    assertThat(new ContextSource(ContextSourceKind.ARTIFACT, "brief"))
        .isEqualTo(new ContextSource(ContextSourceKind.ARTIFACT, "brief"))
        .hasSameHashCodeAs(new ContextSource(ContextSourceKind.ARTIFACT, "brief"));
  }

  @Test
  void theSameReferenceUnderADifferentKindIsADifferentSource() {
    // The pair is the identity: without the kind, "brief" could name either of these.
    assertThat(new ContextSource(ContextSourceKind.ARTIFACT, "brief"))
        .isNotEqualTo(new ContextSource(ContextSourceKind.STATE_KEY, "brief"));
  }

  @Test
  void differentReferencesUnderTheSameKindAreDifferentSources() {
    assertThat(new ContextSource(ContextSourceKind.STATE_KEY, "a"))
        .isNotEqualTo(new ContextSource(ContextSourceKind.STATE_KEY, "b"));
  }

  @Test
  void nullKindIsRejected() {
    assertThatThrownBy(() -> new ContextSource(null, "shared-note"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kind");
  }

  @Test
  void nullReferenceIsRejected() {
    assertThatThrownBy(() -> new ContextSource(ContextSourceKind.STATE_KEY, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ref");
  }

  @Test
  void emptyAndWhitespaceOnlyReferencesAreRejected() {
    assertThatThrownBy(() -> new ContextSource(ContextSourceKind.STATE_KEY, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ref");
    assertThatThrownBy(() -> new ContextSource(ContextSourceKind.STATE_KEY, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ref");
    assertThatThrownBy(() -> new ContextSource(ContextSourceKind.STATE_KEY, "\t\n"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ref");
  }

  @Test
  void surroundingWhitespaceIsRemovedFromTheReference() {
    ContextSource padded = new ContextSource(ContextSourceKind.STATE_KEY, " shared-note ");

    assertThat(padded.ref()).isEqualTo("shared-note");
  }

  @Test
  void tabsAndNewlinesAroundTheReferenceAreRemovedToo() {
    assertThat(new ContextSource(ContextSourceKind.STATE_KEY, "\t\nshared-note\n ").ref())
        .isEqualTo("shared-note");
  }

  @Test
  void aPaddedReferenceAddressesTheSameSourceAsAnUnpaddedOne() {
    // The point of trimming: padding in generated output must not become a second, unresolvable
    // source.
    assertThat(new ContextSource(ContextSourceKind.STATE_KEY, "  shared-note  "))
        .isEqualTo(new ContextSource(ContextSourceKind.STATE_KEY, "shared-note"))
        .hasSameHashCodeAs(new ContextSource(ContextSourceKind.STATE_KEY, "shared-note"));
  }

  @Test
  void whitespaceInsideTheReferenceIsPartOfTheNameAndIsKept() {
    assertThat(new ContextSource(ContextSourceKind.ARTIFACT, " design brief ").ref())
        .isEqualTo("design brief");
  }
}
