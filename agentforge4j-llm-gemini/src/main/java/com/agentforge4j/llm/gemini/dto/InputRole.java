package com.agentforge4j.llm.gemini.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Message role for Gemini request payloads. Wire value is the string sent on the wire (for example
 * {@code "user"} for the single user turn in this client). {@link JsonValue} on {@link #toString()}
 * controls request JSON serialization only; this enum is not used on response DTOs.
 */
@RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Getter
public enum InputRole {
  USER("user");

  private final String value;

  @JsonValue
  @Override
  public String toString() {
    return value;
  }
}
