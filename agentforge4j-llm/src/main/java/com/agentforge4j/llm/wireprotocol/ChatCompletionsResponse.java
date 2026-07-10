// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import java.util.List;

/**
 * Response body for an OpenAI-style chat-completions API.
 * <p>
 * Shared by providers whose chat-completions parsing is strict about unrecognized JSON fields
 * (Azure OpenAI, Mistral); vLLM is deliberately lenient instead (tolerates unknown fields) and is
 * not part of this shared shape &mdash; it keeps its own DTOs.
 *
 * @param error   the error details if failed, or {@code null}
 * @param choices the list of choices
 * @param usage   token usage, or {@code null} when the provider omits it
 * @param model   the model that produced the response, when reported
 */
public record ChatCompletionsResponse(
    ApiError error,
    List<ChatChoice> choices,
    ChatCompletionsUsage usage,
    String model
) {

}
