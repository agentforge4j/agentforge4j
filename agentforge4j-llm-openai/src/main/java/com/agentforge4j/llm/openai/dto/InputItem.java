package com.agentforge4j.llm.openai.dto;

/**
 * Input item for OpenAI responses API.
 *
 * @param role    the role of the input
 * @param content the input content
 */
public record InputItem(
    InputRole role,
    String content
) {
}
