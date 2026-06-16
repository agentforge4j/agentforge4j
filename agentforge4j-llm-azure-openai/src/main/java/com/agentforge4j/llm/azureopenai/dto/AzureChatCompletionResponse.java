// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai.dto;

import java.util.List;

/**
 * Response body for Azure OpenAI chat completions.
 */
public record AzureChatCompletionResponse(
    AzureChatCompletionError error,
    List<AzureChatCompletionChoice> choices,
    AzureChatCompletionUsage usage,
    String model
) {

}
