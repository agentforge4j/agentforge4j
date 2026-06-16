// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request body for the OpenAI-compatible Responses API.
 */
public record OpenAiCompatibleResponsesRequest(String model,
                                   List<OpenAiCompatibleInputItem> input,
                                   @JsonProperty("max_output_tokens") Integer maxOutputTokens) {

}
