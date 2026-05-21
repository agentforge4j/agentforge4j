package com.agentforge4j.llm.openaicompatible.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAiCompatibleResponsesInputTokensDetails(
    @JsonProperty("cached_tokens") Integer cachedTokens
) {

}
