package com.agentforge4j.core.workflow.artifact;

/**
 * Date input field.
 */
public record DateArtifactItem(
    String id,
    String label,
    boolean required
) implements ArtifactItem {

  public DateArtifactItem {
    ArtifactItem.validateBase(id, label);
  }
}
