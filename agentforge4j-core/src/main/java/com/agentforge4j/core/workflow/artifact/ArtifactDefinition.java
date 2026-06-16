// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.artifact;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Defines a collection of input items that the UI renders as a form. Instances are immutable
 * and validated at construction time.
 */
public record ArtifactDefinition(
    String id,
    List<ArtifactItem> items
) {

  public ArtifactDefinition {
    Validate.notBlank(id, "ArtifactDefinition id must not be blank");
    Validate.notEmpty(items,
        "ArtifactDefinition items must not be empty for artifact: %s".formatted(id));
    items = List.copyOf(items);
  }
}
