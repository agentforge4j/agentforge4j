// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.artifact;

import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Defines a collection of input items that the UI renders as a form. Instances are immutable
 * and validated at construction time.
 *
 * <p>The id may not lie in the reserved runtime-owned {@code __} namespace: submitted answers are
 * written to context under {@code <id>.<answerKey>}, so a reserved id would place every answer
 * inside the namespace that backs runtime governance state (and that rewind sweeps deliberately
 * preserve).
 */
public record ArtifactDefinition(
    String id,
    List<ArtifactItem> items
) {

  public ArtifactDefinition {
    Validate.notBlank(id, "ArtifactDefinition id must not be blank");
    Validate.isTrue(!ReservedContextKeys.isReserved(id),
        "ArtifactDefinition id must not use the reserved '%s' namespace: %s"
            .formatted(ReservedContextKeys.RESERVED_PREFIX, id));
    Validate.notEmpty(items,
        "ArtifactDefinition items must not be empty for artifact: %s".formatted(id));
    items = List.copyOf(items);
  }
}
