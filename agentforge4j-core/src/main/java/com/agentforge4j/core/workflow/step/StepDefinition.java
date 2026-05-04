package com.agentforge4j.core.workflow.step;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.util.Validate;

/**
 * One executable step in a workflow: identity, behaviour, optional context mapping, and prompt
 * text.
 *
 * @param stepId         non-blank stable id within the workflow
 * @param name           non-blank display or logical name
 * @param behaviour      discriminated behaviour (agent, spar, nested workflow, and so on)
 * @param contextMapping mapping from context keys; if {@code null} at construction, replaced with
 *                       {@link ContextMapping#none()}
 * @param stepPrompt     optional prompt content for the step; may be blank depending on behaviour
 */
public record StepDefinition(
    String stepId,
    String name,
    StepBehaviour behaviour,
    ContextMapping contextMapping,
    String stepPrompt
) implements Executable {

  public StepDefinition {
    Validate.notBlank(stepId, "StepDefinition stepId must not be blank");
    Validate.notBlank(name, "StepDefinition name must not be blank for step: %s".formatted(stepId));
    Validate.notNull(behaviour, "StepDefinition behaviour must not be null for step: %s".formatted(stepId));
    contextMapping = contextMapping != null ? contextMapping : ContextMapping.none();
  }
}
