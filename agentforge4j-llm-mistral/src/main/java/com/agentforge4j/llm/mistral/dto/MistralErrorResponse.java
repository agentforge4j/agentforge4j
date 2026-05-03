package com.agentforge4j.llm.mistral.dto;

/**
 * Error object embedded in a chat completion error response.
 */
public record MistralErrorResponse(String message, String code, String type) {

}
