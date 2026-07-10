// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage on a chat-completions response ({@code usage}).
 * <p>
 * Shared by providers whose chat-completions parsing is strict about unrecognized JSON fields
 * (Azure OpenAI, Mistral); vLLM is deliberately lenient instead and is not part of this shared
 * shape.
 */
public record ChatCompletionsUsage(
    @JsonProperty("prompt_tokens") Integer promptTokens,
    @JsonProperty("completion_tokens") Integer completionTokens,
    @JsonProperty("total_tokens") Integer totalTokens,
    @JsonProperty("prompt_tokens_details") CachedTokensDetails promptTokensDetails
) {

}
