package com.agentforge4j.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Optional breakdown under Responses API {@code usage.input_tokens_details}.
 */
public record OpenAiResponsesInputTokensDetailsDto(
    @JsonProperty("cached_tokens") Integer cachedTokens
) {

}
