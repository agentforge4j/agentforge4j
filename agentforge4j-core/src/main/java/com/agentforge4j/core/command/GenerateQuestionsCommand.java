package com.agentforge4j.core.command;

import com.agentforge4j.core.workflow.artifact.ArtifactItem;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Return a structured list of questions that the UI should render as an input form.
 *
 * <p>Reuses {@link ArtifactItem} so that generated questions follow the same
 * rendering contract as statically-defined artifacts.
 */
public record GenerateQuestionsCommand(
    @JsonProperty(required = true)
    List<ArtifactItem> questions) implements LlmCommand {

  public GenerateQuestionsCommand {
    Validate.notEmpty(questions, "GenerateQuestionsCommand questions must not be empty");
    questions = List.copyOf(questions);
  }
}
