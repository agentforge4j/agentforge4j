// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage reported by the API (optional on responses).
 */
public record MistralUsage(
    @JsonProperty("prompt_tokens") Integer promptTokens,
    @JsonProperty("completion_tokens") Integer completionTokens,
    @JsonProperty("total_tokens") Integer totalTokens
) {

}
