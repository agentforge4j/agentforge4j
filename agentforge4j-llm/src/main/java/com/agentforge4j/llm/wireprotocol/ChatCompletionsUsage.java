// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage on a chat-completions response ({@code usage}).
 * <p>
 * Shared by every provider whose chat-completions wire shape follows the OpenAI-compatible
 * layout (Azure OpenAI, Mistral, vLLM). Unknown fields are tolerated so a provider adding a
 * response field cannot break parsing, independently of how the embedding application
 * configures its {@code ObjectMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionsUsage(
    @JsonProperty("prompt_tokens") Integer promptTokens,
    @JsonProperty("completion_tokens") Integer completionTokens,
    @JsonProperty("total_tokens") Integer totalTokens,
    @JsonProperty("prompt_tokens_details") CachedTokensDetails promptTokensDetails
) {

}
