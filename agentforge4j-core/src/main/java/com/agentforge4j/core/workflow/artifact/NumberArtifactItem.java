package com.agentforge4j.core.workflow.artifact;

/**
 * Numeric input field.
 */
public record NumberArtifactItem(
    String id,
    String label,
    boolean required
) implements ArtifactItem {

  public NumberArtifactItem {
    ArtifactItem.validateBase(id, label);
  }
}
