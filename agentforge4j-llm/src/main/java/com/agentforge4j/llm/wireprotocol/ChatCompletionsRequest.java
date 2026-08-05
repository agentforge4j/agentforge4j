// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Request body for an OpenAI-style chat-completions API.
 * <p>
 * Shared by Azure OpenAI, Mistral and vLLM. {@code stream} is the one field the three do not
 * agree on: vLLM sends an explicit {@code "stream": false}, while Azure OpenAI and Mistral omit
 * the field entirely. {@link JsonInclude.Include#NON_NULL} is what makes both shapes expressible
 * from one record &mdash; passing {@code null} drops the field, so it must not be removed.
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

}
