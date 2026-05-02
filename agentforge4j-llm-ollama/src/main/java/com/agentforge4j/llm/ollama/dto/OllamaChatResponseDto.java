package com.agentforge4j.llm.ollama.dto;

public record OllamaChatResponseDto(
  MessageDto message,
  String error
) {
}
