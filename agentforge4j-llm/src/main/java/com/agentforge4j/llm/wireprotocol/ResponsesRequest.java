// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for an OpenAI-style Responses API.
 *
 * @param model           the model identifier
 * @param input           the list of input items
 * @param maxOutputTokens optional output token budget ({@code max_output_tokens})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponsesRequest(
    String model,
    List<ResponsesInputItem> input,
    @JsonProperty("max_output_tokens") Integer maxOutputTokens
) {

  public ResponsesRequest(String model, List<ResponsesInputItem> input) {
    this(model, input, null);
  }
}
