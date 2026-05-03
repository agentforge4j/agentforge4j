package com.agentforge4j.llm.openaicompatible.dto;

import java.util.List;

/**
 * Request body for the OpenAI-compatible Responses API.
 */
public record OpenAiCompatibleResponsesRequest(String model,
                                               List<OpenAiCompatibleInputItem> input) {

}
