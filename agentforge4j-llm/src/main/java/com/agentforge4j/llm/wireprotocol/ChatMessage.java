// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

/**
 * Chat message for an OpenAI-style chat-completions API, used both for request messages (role +
 * content) and for a response choice's {@code message} (only {@link #content()} is read there;
 * {@link #role()} may be {@code null} when the provider omits it on responses).
 * <p>
 * Shared by providers whose chat-completions parsing is strict about unrecognized JSON fields
 * (Azure OpenAI, Mistral); vLLM is deliberately lenient instead (see its own {@code dto} package)
 * and is not part of this shared shape.
 */
public record ChatMessage(InputRole role, String content) {

}
