// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Roles for messages in OpenAI-style Responses and chat-completions APIs.
 * <p>
 * Shared across every {@code agentforge4j-llm-*} provider module whose wire protocol follows
 * this OpenAI-style shape (OpenAI, OpenAI-compatible, Azure OpenAI, Mistral, vLLM). Providers
 * that only ever need a subset of these roles (for example a single-turn client that always
 * sends {@link #USER}) simply never reference the other constants.
 * <p>
 * Jackson serializes and deserializes using the API wire strings via {@link JsonValue} on
 * {@link #toString()}.
 */
@RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Getter
public enum InputRole {
  /** System-level instructions or context. */
  SYSTEM("system"),
  /** User-provided input or query. */
  USER("user"),
  /** Assistant-generated response. */
  ASSISTANT("assistant"),
  /** Tool or function call result. */
  TOOL("tool");

  private final String value;

  @JsonValue
  @Override
  public String toString() {
    return value;
  }
}
