// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.artifact;

/**
 * Multi-line text input field.
 */
public record TextAreaArtifactItem(
    String id,
    String label,
    boolean required,
    String hint
) implements ArtifactItem {

  public TextAreaArtifactItem {
    ArtifactItem.validateBase(id, label);
  }
}
