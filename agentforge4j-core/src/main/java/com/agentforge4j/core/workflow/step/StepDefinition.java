package com.agentforge4j.core.workflow.step;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.util.Validate;

/**
 * One executable step in a workflow: identity, behaviour, optional context mapping, and prompt
 * text.
 *
 * @param stepId              non-blank stable id within the workflow
 * @param name                non-blank display or logical name
 * @param behaviour           discriminated behaviour (agent, spar, nested workflow, and so on)
 * @param contextMapping      mapping from context keys; if {@code null} at construction, replaced
 *                            with {@link ContextMapping#none()}
 * @param stepPrompt          optional prompt content for the step; may be blank depending on
 *                            behaviour
 * @param maxUserPromptRounds optional cap on consecutive blocking {@code USER_PROMPT} pauses;
 *                            {@code null} uses the runtime default
 * @param modelTier           optional capability tier name ({@code LITE}/{@code STANDARD}/
 *                            {@code POWERFUL}) that overrides the agent's tier for this step;
 *                            {@code null} inherits the agent tier. Stored as the tier name (a
 *                            String) so {@code core} stays free of the {@code llm-api} enum; the
 *                            name is validated at the runtime invocation boundary
 */
public record StepDefinition(
    String stepId,
    String name,
    StepBehaviour behaviour,
    ContextMapping contextMapping,
    String stepPrompt,
    Integer maxUserPromptRounds,
    String modelTier
) implements Executable {

  public StepDefinition {
    Validate.notBlank(stepId, "StepDefinition stepId must not be blank");
    Validate.notBlank(name, "StepDefinition name must not be blank for step: %s".formatted(stepId));
    Validate.notNull(behaviour,
        "StepDefinition behaviour must not be null for step: %s".formatted(stepId));
    contextMapping = contextMapping != null ? contextMapping : ContextMapping.none();
  }

  /**
   * Returns a new {@link Builder} for assembling a {@link StepDefinition} without positional
   * arguments. Optional fields ({@code contextMapping}, {@code stepPrompt},
   * {@code maxUserPromptRounds}, {@code modelTier}) may be left unset; the required fields are
   * validated when {@link Builder#build()} is called.
   *
   * @return new builder; never {@code null}
   */
  public static Builder builder() {
    return new Builder();
  }

  public static StepDefinition duplicate(StepDefinition step, String stepPrompt) {
    return StepDefinition.builder()
        .withStepId(step.stepId())
        .withName(step.name())
        .withBehaviour(step.behaviour())
        .withContextMapping(step.contextMapping())
        .withStepPrompt(stepPrompt)
        .withMaxUserPromptRounds(step.maxUserPromptRounds())
        .withModelTier(step.modelTier())
        .build();
  }

  /**
   * Fluent builder for {@link StepDefinition}. Lets callers omit the optional {@code modelTier}
   * (and other optional fields) without passing a trailing {@code null} to the canonical
   * constructor. Validation is deferred to {@link #build()}, which delegates to the canonical
   * constructor.
   */
  public static final class Builder {

    private String stepId;
    private String name;
    private StepBehaviour behaviour;
    private ContextMapping contextMapping;
    private String stepPrompt;
    private Integer maxUserPromptRounds;
    private String modelTier;

    private Builder() {
      // obtain via StepDefinition.builder()
    }

    /**
     * Sets the stable step id within the workflow.
     *
     * @param stepId non-blank stable id
     *
     * @return this builder
     */
    public Builder withStepId(String stepId) {
      this.stepId = stepId;
      return this;
    }

    /**
     * Sets the display or logical step name.
     *
     * @param name non-blank name
     *
     * @return this builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the discriminated step behaviour.
     *
     * @param behaviour step behaviour
     *
     * @return this builder
     */
    public Builder withBehaviour(StepBehaviour behaviour) {
      this.behaviour = behaviour;
      return this;
    }

    /**
     * Sets the context mapping.
     *
     * @param contextMapping mapping from context keys; {@code null} becomes
     *                       {@link ContextMapping#none()}
     *
     * @return this builder
     */
    public Builder withContextMapping(ContextMapping contextMapping) {
      this.contextMapping = contextMapping;
      return this;
    }

    /**
     * Sets the optional prompt content for the step.
     *
     * @param stepPrompt prompt content; may be blank depending on behaviour
     *
     * @return this builder
     */
    public Builder withStepPrompt(String stepPrompt) {
      this.stepPrompt = stepPrompt;
      return this;
    }

    /**
     * Sets the optional cap on consecutive blocking {@code USER_PROMPT} pauses.
     *
     * @param maxUserPromptRounds cap; {@code null} uses the runtime default
     *
     * @return this builder
     */
    public Builder withMaxUserPromptRounds(Integer maxUserPromptRounds) {
      this.maxUserPromptRounds = maxUserPromptRounds;
      return this;
    }

    /**
     * Sets the optional step-level capability tier name ({@code LITE}/{@code STANDARD}/
     * {@code POWERFUL}) that overrides the agent tier for this step.
     *
     * @param modelTier capability tier name, or {@code null} to inherit the agent tier
     *
     * @return this builder
     */
    public Builder withModelTier(String modelTier) {
      this.modelTier = modelTier;
      return this;
    }

    /**
     * Builds the validated {@link StepDefinition}.
     *
     * @return immutable step definition; never {@code null}
     */
    public StepDefinition build() {
      return new StepDefinition(stepId, name, behaviour, contextMapping, stepPrompt,
          maxUserPromptRounds, modelTier);
    }
  }
}
