// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of one collection gate's state, held per step on the run's
 * {@link com.agentforge4j.core.workflow.state.WorkflowState}. The event log is the immutable history;
 * this is the derived current view. Runtime operations produce a new instance and store it back —
 * the record itself is never mutated in place.
 *
 * @param stepId            non-blank id of the owning collection step
 * @param phase             non-null intake phase
 * @param openedAt          non-null instant the gate opened
 * @param items             current item slots; never {@code null}, defensively copied
 * @param closedAt          instant the gate closed; {@code null} while {@link CollectionPhase#OPEN}
 * @param closedByActorId   actor that closed the gate; {@code null} while open
 * @param closeReason       why the gate closed; {@code null} while open
 * @param seenClientTokens  client tokens already accepted, for idempotent resubmission; never
 *                          {@code null}, defensively copied
 * @param seenCloseTokens   close tokens already accepted, for idempotent close; never {@code null},
 *                          defensively copied
 * @param version           monotonically increasing mutation counter giving a total order
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionState(
    String stepId,
    CollectionPhase phase,
    Instant openedAt,
    List<CollectionItem> items,
    Instant closedAt,
    String closedByActorId,
    CloseReason closeReason,
    Set<String> seenClientTokens,
    Set<String> seenCloseTokens,
    long version
) {

  public CollectionState {
    Validate.notBlank(stepId, "CollectionState stepId must not be blank");
    Validate.notNull(phase, "CollectionState phase must not be null");
    Validate.notNull(openedAt, "CollectionState openedAt must not be null");
    Validate.isNotNegative(version, "CollectionState version must not be negative");
    if (phase == CollectionPhase.OPEN) {
      Validate.isTrue(closedAt == null,
          "CollectionState OPEN phase must not carry closedAt");
      Validate.isTrue(closedByActorId == null,
          "CollectionState OPEN phase must not carry closedByActorId");
      Validate.isTrue(closeReason == null,
          "CollectionState OPEN phase must not carry closeReason");
    } else {
      Validate.isTrue(closedAt != null,
          "CollectionState CLOSED phase requires closedAt");
      Validate.notBlank(closedByActorId,
          "CollectionState CLOSED phase requires non-blank closedByActorId");
      Validate.isTrue(closeReason != null,
          "CollectionState CLOSED phase requires closeReason");
    }
    items = items != null ? List.copyOf(items) : List.of();
    seenClientTokens = seenClientTokens != null ? Set.copyOf(seenClientTokens) : Set.of();
    seenCloseTokens = seenCloseTokens != null ? Set.copyOf(seenCloseTokens) : Set.of();
  }
}
