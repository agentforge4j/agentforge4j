package com.agentforge4j.llm.azureopenai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Optional token usage on chat completion responses.
 */
public record AzureChatCompletionUsage(
    @JsonProperty("prompt_tokens") Integer promptTokens,
    @JsonProperty("completion_tokens") Integer completionTokens,
    @JsonProperty("total_tokens") Integer totalTokens,
    @JsonProperty("prompt_tokens_details") AzureChatCompletionPromptTokensDetails promptTokensDetails
) {

}
