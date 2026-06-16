// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.artifact;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Checkbox or multi-select choice field accepting zero or more selected values.
 */
public record MultiChoiceArtifactItem(
    String id,
    String label,
    boolean required,
    List<String> options
) implements ArtifactItem {

  public MultiChoiceArtifactItem {
    ArtifactItem.validateBase(id, label);
    Validate.notEmpty(options, "MultiChoiceArtifactItem options must not be empty for item: %s".formatted(id));
    options = List.copyOf(options);
  }
}
