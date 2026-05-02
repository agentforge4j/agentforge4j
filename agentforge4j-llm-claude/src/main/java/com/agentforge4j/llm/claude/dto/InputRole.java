package com.agentforge4j.llm.claude.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Getter
public enum InputRole {
  USER("user");

  private final String value;

  public String toString() {
    return value;
  }
}
