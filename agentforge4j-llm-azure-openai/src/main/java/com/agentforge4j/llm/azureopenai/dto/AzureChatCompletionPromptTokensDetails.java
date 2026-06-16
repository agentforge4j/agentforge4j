// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AzureChatCompletionPromptTokensDetails(
    @JsonProperty("cached_tokens") Integer cachedTokens
) {

}
