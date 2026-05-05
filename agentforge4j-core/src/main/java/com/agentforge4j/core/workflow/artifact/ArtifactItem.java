package com.agentforge4j.core.workflow.artifact;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed marker interface for form input items, dispatched by type for rendering.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextArtifactItem.class, name = "TEXT"),
    @JsonSubTypes.Type(value = TextAreaArtifactItem.class, name = "TEXT_AREA"),
    @JsonSubTypes.Type(value = SingleChoiceArtifactItem.class, name = "SINGLE_CHOICE"),
    @JsonSubTypes.Type(value = MultiChoiceArtifactItem.class, name = "MULTI_CHOICE"),
    @JsonSubTypes.Type(value = BooleanArtifactItem.class, name = "BOOLEAN"),
    @JsonSubTypes.Type(value = NumberArtifactItem.class, name = "NUMBER"),
    @JsonSubTypes.Type(value = DateArtifactItem.class, name = "DATE")
})
public sealed interface ArtifactItem
    permits TextArtifactItem,
    TextAreaArtifactItem,
    SingleChoiceArtifactItem,
    MultiChoiceArtifactItem,
    BooleanArtifactItem,
    NumberArtifactItem,
    DateArtifactItem {

  String id();

  String label();

  boolean required();

  /**
   * Validates the id and label fields common to all artifact item types.
   *
   * @param id the artifact item id
   * @param label the artifact item label
   */
  static void validateBase(String id, String label) {
    Validate.notBlank(id, "ArtifactItem id must not be blank");
    Validate.notBlank(label, "ArtifactItem label must not be blank for item: %s".formatted(id));
  }
}
