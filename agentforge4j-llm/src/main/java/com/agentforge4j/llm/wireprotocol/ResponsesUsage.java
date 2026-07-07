// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage on Responses API payloads ({@code usage}).
 */
public record ResponsesUsage(
    @JsonProperty("input_tokens") Integer inputTokens,
    @JsonProperty("output_tokens") Integer outputTokens,
    @JsonProperty("input_tokens_details") CachedTokensDetails inputTokensDetails
) {

}
