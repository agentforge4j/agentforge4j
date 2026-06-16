// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Roles for messages in OpenAI-style chat completions.
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
