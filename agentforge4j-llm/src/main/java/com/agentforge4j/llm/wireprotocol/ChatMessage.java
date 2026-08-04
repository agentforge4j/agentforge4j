// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Chat message for an OpenAI-style chat-completions API, used both for request messages (role +
 * content) and for a response choice's {@code message} (only {@link #content()} is read there;
 * {@link #role()} may be {@code null} when the provider omits it on responses).
 * <p>
 * Shared by every provider whose chat-completions wire shape follows the OpenAI-compatible
 * layout (Azure OpenAI, Mistral, vLLM). Unknown fields are tolerated so a provider adding a
 * response field cannot break parsing, independently of how the embedding application
 * configures its {@code ObjectMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(InputRole role, String content) {

}
