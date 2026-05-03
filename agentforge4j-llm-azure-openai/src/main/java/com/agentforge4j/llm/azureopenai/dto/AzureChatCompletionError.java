package com.agentforge4j.llm.azureopenai.dto;

/**
 * Error object embedded in a chat completion response.
 */
public record AzureChatCompletionError(String message, String code, String type) {

}
