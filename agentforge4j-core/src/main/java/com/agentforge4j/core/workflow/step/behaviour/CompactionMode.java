// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * How a compaction step produces its compact sibling, discriminated in JSON by {@code type}. The mode
 * split makes the LLM tier structurally required for {@link LlmSummary} and structurally absent for
 * {@link DeterministicExtract}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DeterministicExtract.class, name = "DETERMINISTIC_EXTRACT"),
    @JsonSubTypes.Type(value = LlmSummary.class, name = "LLM_SUMMARY")
})
public sealed interface CompactionMode permits DeterministicExtract, LlmSummary {

}
