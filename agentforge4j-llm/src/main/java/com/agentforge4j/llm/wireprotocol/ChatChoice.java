// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One choice from a chat-completions response.
 * <p>
 * Shared by every provider whose chat-completions wire shape follows the OpenAI-compatible
 * layout (Azure OpenAI, Mistral, vLLM). Unknown fields are tolerated so a provider adding a
 * response field cannot break parsing, independently of how the embedding application
 * configures its {@code ObjectMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatChoice(ChatMessage message) {

}
