// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage on OpenAI Responses API payloads ({@code usage}).
 */
public record OpenAiResponsesUsageDto(
    @JsonProperty("input_tokens") Integer inputTokens,
    @JsonProperty("output_tokens") Integer outputTokens,
    @JsonProperty("input_tokens_details") OpenAiResponsesInputTokensDetailsDto inputTokensDetails
) {

}
