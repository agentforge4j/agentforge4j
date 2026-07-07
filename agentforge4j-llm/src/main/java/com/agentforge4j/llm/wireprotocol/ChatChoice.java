// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

/**
 * One choice from a chat-completions response.
 * <p>
 * Shared by providers whose chat-completions parsing is strict about unrecognized JSON fields
 * (Azure OpenAI, Mistral); vLLM is deliberately lenient instead and is not part of this shared
 * shape.
 */
public record ChatChoice(ChatMessage message) {

}
