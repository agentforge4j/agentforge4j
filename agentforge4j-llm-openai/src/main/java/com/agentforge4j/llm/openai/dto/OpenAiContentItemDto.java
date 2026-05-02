package com.agentforge4j.llm.openai.dto;

/**
 * Content item in OpenAI responses API.
 *
 * @param type the content type (e.g., "text")
 * @param text the text content
 */
public record OpenAiContentItemDto(
    String type,
    String text
) {

}
