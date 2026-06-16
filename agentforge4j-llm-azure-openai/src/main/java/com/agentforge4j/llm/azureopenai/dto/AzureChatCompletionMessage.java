// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai.dto;

/**
 * Message entry for Azure OpenAI chat completion requests.
 */
public record AzureChatCompletionMessage(InputRole role, String content) {

}
