package com.agentforge4j.llm.azureopenai.dto;

import java.util.List;

/**
 * Request body for Azure OpenAI chat completions.
 */
public record AzureChatCompletionRequest(String model, List<AzureChatCompletionMessage> messages) {

}
