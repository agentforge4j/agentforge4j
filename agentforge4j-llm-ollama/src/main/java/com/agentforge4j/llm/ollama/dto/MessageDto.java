package com.agentforge4j.llm.ollama.dto;

public record MessageDto(
  InputRole role,
  String content
) {
  public String getRole() {
    return role.toString();
  }
}
