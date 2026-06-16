// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai.dto;

/**
 * Message entry for OpenAI-style chat completion requests (Azure OpenAI, Mistral, and similar).
 *
 * @param role    the role of the message sender (e.g., user, assistant)
 * @param content the text content of the message
 */
public record OpenAiChatCompletionMessageDto(
    InputRole role,
    String content
) {

}
