package com.agentforge4j.llm.openai.dto;

/**
 * Assistant message shape inside a chat completion choice.
 *
 * @param role    the role of the message (typically "assistant")
 * @param content the generated text content
 */
public record OpenAiChatCompletionMessageResponseDto(
    InputRole role,
    String content
) {

}
