package com.agentforge4j.llm.openai.dto;

import java.util.List;

/**
 * Request body for OpenAI responses API.
 *
 * @param model the model identifier
 * @param input the list of input items
 */
public record OpenAiResponsesRequestDto(
    String model,
    List<InputItem> input
) {

}
