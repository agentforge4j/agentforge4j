package com.agentforge4j.llm.mistral.dto;

import java.util.List;

/**
 * Response body for Mistral OpenAI-compatible chat completions.
 */
public record MistralChatResponse(
    MistralErrorResponse error,
    List<MistralChoice> choices,
    MistralUsage usage
) {

}
