// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage on OpenAI-compatible Responses API payloads ({@code usage}).
 */
public record OpenAiCompatibleResponsesUsage(
    @JsonProperty("input_tokens") Integer inputTokens,
    @JsonProperty("output_tokens") Integer outputTokens,
    @JsonProperty("input_tokens_details") OpenAiCompatibleResponsesInputTokensDetails inputTokensDetails
) {

}
