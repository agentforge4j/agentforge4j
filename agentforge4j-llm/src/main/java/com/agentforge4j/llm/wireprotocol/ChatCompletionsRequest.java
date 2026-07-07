// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Request body for an OpenAI-style chat-completions API.
 * <p>
 * Shared by Azure OpenAI and Mistral; vLLM keeps its own request DTO since it always sends an
 * explicit {@code "stream": false} field rather than omitting it.
 *
 * @param model    the model identifier
 * @param messages the conversation messages
 * @param stream   whether to stream the response, or {@code null} to omit the field entirely
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionsRequest(
    String model,
    List<ChatMessage> messages,
    Boolean stream
) {

  public ChatCompletionsRequest(String model, List<ChatMessage> messages) {
    this(model, messages, null);
  }
}
