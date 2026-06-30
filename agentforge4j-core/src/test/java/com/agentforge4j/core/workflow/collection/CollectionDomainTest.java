// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionDomainTest {

  private static final Instant NOW = Instant.parse("2026-06-23T00:00:00Z");

  @Test
  void fileRefValidatesPathFilenameAndSize() {
    FileRef ref = new FileRef("runs/r1/cv.pdf", "cv.pdf", "application/pdf", 1024L);
    assertThat(ref.path()).isEqualTo("runs/r1/cv.pdf");
    assertThat(ref.contentType()).isEqualTo("application/pdf");

    assertThatThrownBy(() -> new FileRef(" ", "cv.pdf", "application/pdf", 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FileRef("p", " ", "application/pdf", 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FileRef("p", "cv.pdf", "application/pdf", -1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void collectionPayloadDefaultsFilesAndReportsEmptiness() {
    CollectionPayload empty = new CollectionPayload(null, null);
    assertThat(empty.files()).isEmpty();
    assertThat(empty.isEmpty()).isTrue();

    CollectionPayload withInline = new CollectionPayload("{\"k\":1}", null);
    assertThat(withInline.isEmpty()).isFalse();

    CollectionPayload withFile =
        new CollectionPayload(null, List.of(new FileRef("p", "f", null, 0L)));
    assertThat(withFile.isEmpty()).isFalse();
    assertThat(withFile.files()).hasSize(1);
  }

  @Test
  void collectionItemValidatesRequiredFields() {
    CollectionPayload payload = new CollectionPayload("{}", null);
    CollectionItem item = new CollectionItem("s1", "actor-1", NOW, 1, false, payload, null, "tok");
    assertThat(item.version()).isEqualTo(1);
    assertThat(item.clientToken()).isEqualTo("tok");

    assertThatThrownBy(() -> new CollectionItem(" ", "a", NOW, 1, false, payload, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CollectionItem("s1", " ", NOW, 1, false, payload, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CollectionItem("s1", "a", null, 1, false, payload, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CollectionItem("s1", "a", NOW, 0, false, payload, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CollectionItem("s1", "a", NOW, 1, false, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void collectionStateDefaultsCollectionsToImmutableEmpty() {
    CollectionState state =
        new CollectionState("step-1", CollectionPhase.OPEN, NOW, null, null, null, null, null, null, 0L);
    assertThat(state.items()).isEmpty();
    assertThat(state.seenClientTokens()).isEmpty();
    assertThat(state.seenCloseTokens()).isEmpty();
    assertThatThrownBy(() -> state.items().add(
        new CollectionItem("s", "a", NOW, 1, false, new CollectionPayload("{}", null), null, null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void collectionStateValidatesRequiredFields() {
    assertThatThrownBy(() ->
        new CollectionState(" ", CollectionPhase.OPEN, NOW, null, null, null, null, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
        new CollectionState("s", null, NOW, null, null, null, null, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
        new CollectionState("s", CollectionPhase.OPEN, null, null, null, null, null, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
        new CollectionState("s", CollectionPhase.OPEN, NOW, null, null, null, null, null, null, -1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void closedStateCarriesCloseMetadata() {
    CollectionState closed = new CollectionState("step-1", CollectionPhase.CLOSED, NOW,
        List.of(), NOW.plusSeconds(60), "coordinator", CloseReason.MANUAL,
        Set.of("ct1"), Set.of("close1"), 3L);
    assertThat(closed.phase()).isEqualTo(CollectionPhase.CLOSED);
    assertThat(closed.closedByActorId()).isEqualTo("coordinator");
    assertThat(closed.closeReason()).isEqualTo(CloseReason.MANUAL);
    assertThat(closed.seenClientTokens()).containsExactly("ct1");
  }

  @Test
  void openStateCarriesNoCloseMetadata() {
    CollectionState open = new CollectionState("step-1", CollectionPhase.OPEN, NOW,
        null, null, null, null, null, null, 0L);
    assertThat(open.closedAt()).isNull();
    assertThat(open.closedByActorId()).isNull();
    assertThat(open.closeReason()).isNull();
  }

  @Test
  void openPhaseRejectsAnyCloseMetadata() {
    assertThatThrownBy(() -> new CollectionState("step-1", CollectionPhase.OPEN, NOW,
        null, NOW, null, null, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CollectionState("step-1", CollectionPhase.OPEN, NOW,
        null, null, "coordinator", null, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CollectionState("step-1", CollectionPhase.OPEN, NOW,
        null, null, null, CloseReason.MANUAL, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void closedPhaseRequiresEachCloseMetadataField() {
    assertThatThrownBy(() -> new CollectionState("step-1", CollectionPhase.CLOSED, NOW,
        null, null, "coordinator", CloseReason.MANUAL, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CollectionState("step-1", CollectionPhase.CLOSED, NOW,
        null, NOW, null, CloseReason.MANUAL, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CollectionState("step-1", CollectionPhase.CLOSED, NOW,
        null, NOW, " ", CloseReason.MANUAL, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CollectionState("step-1", CollectionPhase.CLOSED, NOW,
        null, NOW, "coordinator", null, null, null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
