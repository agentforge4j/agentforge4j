package com.agentforge4j.core.workflow.artifact;

/**
 * Single-line text input field.
 */
public record TextArtifactItem(
    String id,
    String label,
    boolean required,
    String hint
) implements ArtifactItem {

  public TextArtifactItem {
    ArtifactItem.validateBase(id, label);
  }
}
