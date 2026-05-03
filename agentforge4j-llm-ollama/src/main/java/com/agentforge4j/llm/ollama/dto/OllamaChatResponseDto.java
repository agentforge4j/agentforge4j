package com.agentforge4j.llm.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponseDto(
  MessageDto message,
  String error
) {
}
