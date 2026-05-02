package com.agentforge4j.llm.ollama.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Roles for Ollama chat messages. Wire values match the Ollama REST API (lowercase strings).
 * <p>
 * Jackson serializes and deserializes using {@link #toString()} via {@link JsonValue}, consistent
 * with {@code agentforge4j-llm-openai}'s {@code InputRole} pattern.
 */
@RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Getter
public enum InputRole {
  SYSTEM("system"),
  USER("user"),
  /** Reply role returned by Ollama in chat responses. */
  ASSISTANT("assistant");

  private final String value;

  @JsonValue
  @Override
  public String toString() {
    return value;
  }
}
