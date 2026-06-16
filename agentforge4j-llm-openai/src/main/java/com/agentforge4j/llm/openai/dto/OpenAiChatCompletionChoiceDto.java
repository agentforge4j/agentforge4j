// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai.dto;

/**
 * One choice from a chat completion response.
 *
 * @param message the generated message for this choice
 */
public record OpenAiChatCompletionChoiceDto(
    OpenAiChatCompletionMessageResponseDto message
) {

}
