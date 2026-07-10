// SPDX-License-Identifier: Apache-2.0
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
 * @param estimatedInputTokens  optional usage-site hint: expected input tokens this step consumes,
 *                            for execution estimation; {@code null} when no hint is supplied. When
 *                            present it must not be negative. Advisory only — it never affects
 *                            runtime behaviour
 * @param estimatedOutputTokens optional usage-site hint: expected output tokens this step
 *                            generates, for execution estimation; {@code null} when no hint is
 *                            supplied. When present it must not be negative. Advisory only — it
 *                            never affects runtime behaviour
 * @param contextSelection    optional declaration of the context this step receives (and may
 *                            request); {@code null} keeps the current full-context behaviour
 */
public record StepDefinition(
    String stepId,
    String name,
    StepBehaviour behaviour,
    ContextMapping contextMapping,
    String stepPrompt,
    Integer maxUserPromptRounds,
    String modelTier,
    Integer estimatedInputTokens,
    Integer estimatedOutputTokens,
    ContextSelection contextSelection
) implements Executable {

  public StepDefinition {
    Validate.notBlank(stepId, "StepDefinition stepId must not be blank");
    Validate.notBlank(name, "StepDefinition name must not be blank for step: %s".formatted(stepId));
    Validate.notNull(behaviour,
        "StepDefinition behaviour must not be null for step: %s".formatted(stepId));
    contextMapping = contextMapping != null ? contextMapping : ContextMapping.none();
    if (estimatedInputTokens != null) {
      Validate.isNotNegative(estimatedInputTokens,
          "StepDefinition estimatedInputTokens must not be negative for step: %s".formatted(stepId));
    }
    if (estimatedOutputTokens != null) {
      Validate.isNotNegative(estimatedOutputTokens,
          "StepDefinition estimatedOutputTokens must not be negative for step: %s"
              .formatted(stepId));
    }
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
        .withEstimatedInputTokens(step.estimatedInputTokens())
        .withEstimatedOutputTokens(step.estimatedOutputTokens())
        .withContextSelection(step.contextSelection())
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
    private Integer estimatedInputTokens;
    private Integer estimatedOutputTokens;
    private ContextSelection contextSelection;

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
     * Sets the optional execution-estimation hint for input tokens consumed by this step.
     *
     * @param estimatedInputTokens expected input tokens, or {@code null} for no hint; when present,
     *                             must not be negative
     *
     * @return this builder
     */
    public Builder withEstimatedInputTokens(Integer estimatedInputTokens) {
      this.estimatedInputTokens = estimatedInputTokens;
      return this;
    }

    /**
     * Sets the optional execution-estimation hint for output tokens generated by this step.
     *
     * @param estimatedOutputTokens expected output tokens, or {@code null} for no hint; when
     *                              present, must not be negative
     *
     * @return this builder
     */
    public Builder withEstimatedOutputTokens(Integer estimatedOutputTokens) {
      this.estimatedOutputTokens = estimatedOutputTokens;
      return this;
    }

    /**
     * Sets the optional context selection declaring the context this step receives.
     *
     * @param contextSelection context selection, or {@code null} to keep full-context behaviour
     *
     * @return this builder
     */
    public Builder withContextSelection(ContextSelection contextSelection) {
      this.contextSelection = contextSelection;
      return this;
    }

    /**
     * Builds the validated {@link StepDefinition}.
     *
     * @return immutable step definition; never {@code null}
     */
    public StepDefinition build() {
      return new StepDefinition(stepId, name, behaviour, contextMapping, stepPrompt,
          maxUserPromptRounds, modelTier, estimatedInputTokens, estimatedOutputTokens,
          contextSelection);
    }
  }
}
