package com.agentforge4j.llm.openai.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Roles for messages in OpenAI-style chat completions.
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

  public String toString() {
    return value;
  }
}
