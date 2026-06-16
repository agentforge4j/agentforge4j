// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed marker interface for workflow components: steps, blueprints, and workflows themselves.
 * Dispatched by the runtime based on the {@code kind} discriminator.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StepDefinition.class, name = "STEP"),
    @JsonSubTypes.Type(value = BlueprintRef.class, name = "BLUEPRINT_REF"),
    @JsonSubTypes.Type(value = BlueprintDefinition.class, name = "BLUEPRINT"),
    @JsonSubTypes.Type(value = WorkflowDefinition.class, name = "WORKFLOW")
})
public sealed interface Executable
    permits WorkflowDefinition,
    StepDefinition,
    BlueprintDefinition,
    BlueprintRef {

}
