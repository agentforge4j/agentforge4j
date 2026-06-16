// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.artifact;

/**
 * Boolean artifact field with shared {@link ArtifactItem} id and label rules.
 *
 * @param id non-blank stable id
 * @param label non-blank display label
 * @param required whether user input is mandatory before the step can complete
 */
public record BooleanArtifactItem(
    String id,
    String label,
    boolean required
) implements ArtifactItem {

  public BooleanArtifactItem {
    ArtifactItem.validateBase(id, label);
  }
}
