// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for OpenAI responses API.
 *
 * @param model the model identifier
 * @param input the list of input items
 * @param maxOutputTokens optional output token budget ({@code max_output_tokens})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiResponsesRequestDto(
    String model,
    List<InputItem> input,
    @JsonProperty("max_output_tokens") Integer maxOutputTokens
) {

  public OpenAiResponsesRequestDto(String model, List<InputItem> input) {
    this(model, input, null);
  }
}
