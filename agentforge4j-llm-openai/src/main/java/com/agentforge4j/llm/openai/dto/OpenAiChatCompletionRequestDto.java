// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai.dto;

import java.util.List;

/**
 * Request body for OpenAI-style {@code /v1/chat/completions} APIs.
 *
 * @param model    the model identifier to use for completion
 * @param messages the conversation messages to complete
 */
public record OpenAiChatCompletionRequestDto(
  String model,
  List<OpenAiChatCompletionMessageDto> messages
) {
}
