package com.agentforge4j.llm.ollama.dto;

import java.util.List;

public record OllamaChatRequestDto(
  String model,
  boolean stream,
  List<MessageDto> messages
) {
}
