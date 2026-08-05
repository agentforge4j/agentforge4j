// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Optional cached-token breakdown nested under a usage block (Responses API
 * {@code usage.input_tokens_details} or chat-completions {@code usage.prompt_tokens_details}).
 *
 * @param cachedTokens number of cached input/prompt tokens, when reported
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CachedTokensDetails(
    @JsonProperty("cached_tokens") Integer cachedTokens
) {

}
