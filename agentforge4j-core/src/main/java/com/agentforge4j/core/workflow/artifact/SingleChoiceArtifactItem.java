// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.artifact;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Dropdown or radio button choice field accepting a single selected value.
 */
public record SingleChoiceArtifactItem(
    String id,
    String label,
    boolean required,
    List<String> options
) implements ArtifactItem {

  public SingleChoiceArtifactItem {
    ArtifactItem.validateBase(id, label);
    Validate.notEmpty(options,
        "SingleChoiceArtifactItem options must not be empty for item: %s".formatted(id));
    options = List.copyOf(options);
  }
}
