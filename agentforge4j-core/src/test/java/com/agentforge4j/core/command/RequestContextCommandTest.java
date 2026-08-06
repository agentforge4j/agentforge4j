// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.core.command.schema.LlmCommandSubtypeRegistry;
import com.agentforge4j.core.workflow.context.ContextSource;
import com.agentforge4j.core.workflow.context.ContextSourceKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the request contract: what a request carries, what it refuses, and the fact that nothing
 * in the command pipeline can reach it.
 */
class RequestContextCommandTest {

  private static final ContextSource NOTE =
      new ContextSource(ContextSourceKind.STATE_KEY, "shared-note");
  private static final ContextSource BRIEF =
      new ContextSource(ContextSourceKind.ARTIFACT, "brief");

  @Test
  void carriesTheRequestedSources() {
    RequestContextCommand command = new RequestContextCommand(List.of(NOTE));

    assertThat(command.requestedSources()).containsExactly(NOTE);
  }

  @Test
  void preservesRequestOrder() {
    RequestContextCommand command = new RequestContextCommand(List.of(BRIEF, NOTE));

    assertThat(command.requestedSources()).containsExactly(BRIEF, NOTE);
  }

  @Test
  void keepsARepeatedSourceAsTwoSeparateRequests() {
    RequestContextCommand command = new RequestContextCommand(List.of(NOTE, NOTE));

    assertThat(command.requestedSources()).containsExactly(NOTE, NOTE);
  }

  @Test
  void twoEntriesDifferingOnlyByPaddingRequestTheSameSourceTwice() {
    // Trimming happens on the source, so padding cannot smuggle in a second distinct entry; the
    // repeat is still kept as a repeat.
    RequestContextCommand command = new RequestContextCommand(
        List.of(new ContextSource(ContextSourceKind.STATE_KEY, " shared-note "), NOTE));

    assertThat(command.requestedSources()).containsExactly(NOTE, NOTE);
  }

  @Test
  void equalRequestsAreEqual() {
    assertThat(new RequestContextCommand(List.of(NOTE, BRIEF)))
        .isEqualTo(new RequestContextCommand(List.of(NOTE, BRIEF)))
        .hasSameHashCodeAs(new RequestContextCommand(List.of(NOTE, BRIEF)));
  }

  @Test
  void requestOrderIsPartOfTheRequestIdentity() {
    assertThat(new RequestContextCommand(List.of(NOTE, BRIEF)))
        .isNotEqualTo(new RequestContextCommand(List.of(BRIEF, NOTE)));
  }

  @Test
  void changingTheCallersListAfterwardsDoesNotChangeTheRequest() {
    List<ContextSource> caller = new ArrayList<>(List.of(NOTE));

    RequestContextCommand command = new RequestContextCommand(caller);
    caller.clear();

    assertThat(command.requestedSources()).containsExactly(NOTE);
  }

  @Test
  void theRequestedSourcesCannotBeModifiedThroughTheAccessor() {
    RequestContextCommand command = new RequestContextCommand(List.of(NOTE));

    assertThatThrownBy(() -> command.requestedSources().add(BRIEF))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullRequestedSourcesIsRejected() {
    assertThatThrownBy(() -> new RequestContextCommand(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requestedSources");
  }

  @Test
  void anEmptyRequestIsRejected() {
    assertThatThrownBy(() -> new RequestContextCommand(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requestedSources");
  }

  @Test
  void aNullSourceInTheRequestIsRejected() {
    List<ContextSource> withNull = new ArrayList<>();
    withNull.add(NOTE);
    withNull.add(null);

    assertThatThrownBy(() -> new RequestContextCommand(withNull))
        .isInstanceOf(NullPointerException.class);
  }

  /**
   * Guards the boundary this type is built on: it is a request model and nothing more. If it is
   * ever joined to the command hierarchy, that has to be a deliberate change made together with the
   * schema, parsing and dispatch that a live command needs — this test is what makes that
   * deliberate instead of accidental.
   */
  @Test
  void noCommandPipelineCanReachThisRequest() {
    // Not a command type, so the registry — whose values are command classes — cannot hold it.
    assertThat(LlmCommand.class.isAssignableFrom(RequestContextCommand.class)).isFalse();
    // And no command type has claimed the name either.
    assertThat(LlmCommandSubtypeRegistry.allTypeNamesOrdered()).doesNotContain("REQUEST_CONTEXT");
  }
}
