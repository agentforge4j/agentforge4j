package com.agentforge4j.llm.openai.dto;

/**
 * Error details from an OpenAI API response.
 *
 * @param message a human-readable error message
 * @param code    the error code (e.g., "invalid_request_error")
 * @param type    the error type category
 */
public record OpenAiErrorDto(
    String message,
    String code,
    String type
) {

}
