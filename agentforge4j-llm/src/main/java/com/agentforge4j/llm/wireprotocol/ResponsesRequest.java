// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for an OpenAI-style Responses API.
 * <p>
 * {@link JsonInclude.Include#NON_NULL} is part of the wire contract, not a formatting detail: when
 * no output-token budget is requested the {@code max_output_tokens} key is omitted entirely rather
 * than sent as an explicit {@code null}. OpenAI-compatible servers that validate parameter types
 * reject an explicit null, so omission is the interoperable shape.
 *
 * @param model           the model identifier
 * @param input           the list of input items
 * @param maxOutputTokens optional output token budget ({@code max_output_tokens}); when
 *                        {@code null} the key is omitted from the serialized body
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponsesRequest(
    String model,
    List<ResponsesInputItem> input,
    @JsonProperty("max_output_tokens") Integer maxOutputTokens
) {
}
