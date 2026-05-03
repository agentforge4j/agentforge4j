package com.agentforge4j.llm.mistral.dto;

/**
 * Chat message for Mistral OpenAI-compatible chat completions (request and response shapes).
 */
public record MistralMessage(InputRole role, String content) {

}
