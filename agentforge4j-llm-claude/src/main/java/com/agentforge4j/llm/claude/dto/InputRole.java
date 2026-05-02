package com.agentforge4j.llm.claude.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Message role for Claude requests. Wire value matches the Messages API ({@code "user"} for the
 * single user turn in this client).
 * <p>
 * Jackson uses {@link #toString()} via {@link JsonValue} for serialization and deserialization.
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
