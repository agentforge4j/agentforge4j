// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic model of how a {@link com.agentforge4j.core.workflow.step.StepDefinition} executes,
 * discriminated in JSON by {@code type}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AgentBehaviour.class, name = "AGENT"),
    @JsonSubTypes.Type(value = SparBehaviour.class, name = "SPAR"),
    @JsonSubTypes.Type(value = WorkflowBehaviour.class, name = "WORKFLOW"),
    @JsonSubTypes.Type(value = InputBehaviour.class, name = "INPUT"),
    @JsonSubTypes.Type(value = ResourceBehaviour.class, name = "RESOURCE"),
    @JsonSubTypes.Type(value = BranchBehaviour.class, name = "BRANCH"),
    @JsonSubTypes.Type(value = FailBehaviour.class, name = "FAIL"),
    @JsonSubTypes.Type(value = RetryPreviousBehaviour.class, name = "RETRY_PREVIOUS"),
    @JsonSubTypes.Type(value = ValidateBehaviour.class, name = "VALIDATE"),
    @JsonSubTypes.Type(value = AssignContextBehaviour.class, name = "ASSIGN_CONTEXT"),
    @JsonSubTypes.Type(value = CollectionBehaviour.class, name = "COLLECTION"),
    @JsonSubTypes.Type(value = CompactBehaviour.class, name = "COMPACT")
})
public sealed interface StepBehaviour permits AgentBehaviour, SparBehaviour, WorkflowBehaviour,
    InputBehaviour, ResourceBehaviour, BranchBehaviour, FailBehaviour, RetryPreviousBehaviour,
    ValidateBehaviour, AssignContextBehaviour, CollectionBehaviour, CompactBehaviour {

}
