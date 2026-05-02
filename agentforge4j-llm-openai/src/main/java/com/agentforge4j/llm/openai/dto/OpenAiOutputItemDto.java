package com.agentforge4j.llm.openai.dto;

import java.util.List;

/**
 * Output item in OpenAI responses API.
 *
 * @param type    the output type (e.g., "message")
 * @param content the list of content items
 */
public record OpenAiOutputItemDto(
    String type,
    List<OpenAiContentItemDto> content
) {

}
