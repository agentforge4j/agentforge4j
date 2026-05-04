package com.agentforge4j.core.workflow.artifact;

public record BooleanArtifactItem(
    String id,
    String label,
    boolean required
) implements ArtifactItem {

  public BooleanArtifactItem {
    ArtifactItem.validateBase(id, label);
  }
}
