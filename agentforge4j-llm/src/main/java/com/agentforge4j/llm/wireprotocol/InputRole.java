// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Canonical message role, replacing what were eight near-identical per-provider copies.
 * <p>
 * Used by every provider module that names a message role on the wire, whether or not that
 * provider speaks an OpenAI-style protocol: OpenAI, OpenAI-compatible, Azure OpenAI, Mistral and
 * vLLM via the shared {@code wireprotocol} DTOs, and Claude, Gemini and Ollama via their own
 * provider DTOs. Providers that only ever <em>send</em> a subset of these roles (for example a
 * single-turn client that always sends {@link #USER}) simply never reference the other constants.
 * <p>
 * The enum sits on response-side DTOs as well as request-side ones &mdash; Gemini's
 * {@code GeminiContent} and the shared {@code ChatMessage} are both deserialized from provider
 * payloads &mdash; so the constant set has to cover every role a provider may <em>report</em>, not
 * only the ones this framework sends.
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
