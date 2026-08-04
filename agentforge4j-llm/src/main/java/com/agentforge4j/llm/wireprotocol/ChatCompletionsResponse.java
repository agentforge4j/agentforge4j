// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response body for an OpenAI-style chat-completions API.
 * <p>
 * Shared by every provider whose chat-completions wire shape follows the OpenAI-compatible
 * layout (Azure OpenAI, Mistral, vLLM). Unknown fields are tolerated so a provider adding a
 * response field cannot break parsing, independently of how the embedding application
 * configures its {@code ObjectMapper}.
 *
 * @param error   the error details if failed, or {@code null}
 * @param choices the list of choices
 * @param usage   token usage, or {@code null} when the provider omits it
 * @param model   the model that produced the response, when reported
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionsResponse(
    ApiError error,
    List<ChatChoice> choices,
    ChatCompletionsUsage usage,
    String model
) {

}
