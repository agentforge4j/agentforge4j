// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.runtime;

import com.agentforge4j.core.workflow.collection.CloseReason;
import com.agentforge4j.core.workflow.collection.CollectionItem;
import com.agentforge4j.core.workflow.collection.CollectionPhase;
import java.util.List;

/**
 * Read view of a collection gate returned to an authorized viewer.
 *
 * @param stepId      the collection step id
 * @param phase       the current intake phase
 * @param items       the live (non-withdrawn) item slots, latest version each; never {@code null}
 * @param liveCount   number of live items ({@code items.size()})
 * @param closeReason why the gate closed, or {@code null} while open
 */
public record CollectionView(String stepId, CollectionPhase phase, List<CollectionItem> items,
    int liveCount, CloseReason closeReason) {

  public CollectionView {
    items = items != null ? List.copyOf(items) : List.of();
  }
}
