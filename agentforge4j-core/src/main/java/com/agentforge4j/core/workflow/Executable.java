// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed marker interface for directly placeable workflow components: steps, blueprint references,
 * and nested workflows. Dispatched by the runtime based on the {@code kind} discriminator.
 *
 * <p>A {@link com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition} is intentionally
 * <em>not</em> an {@code Executable}: blueprints are standalone, reusable subgraphs declared in a
 * workflow's {@code blueprints()} map and included via a {@link BlueprintRef}, never embedded
 * inline in a {@code steps()} array.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StepDefinition.class, name = "STEP"),
    @JsonSubTypes.Type(value = BlueprintRef.class, name = "BLUEPRINT_REF"),
    @JsonSubTypes.Type(value = WorkflowDefinition.class, name = "WORKFLOW")
})
public sealed interface Executable
    permits WorkflowDefinition,
    StepDefinition,
    BlueprintRef {

}
