// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VllmUsage(
    @JsonProperty("prompt_tokens") Integer promptTokens,
    @JsonProperty("completion_tokens") Integer completionTokens,
    @JsonProperty("prompt_tokens_details") VllmPromptTokensDetails promptTokensDetails
) {

}
