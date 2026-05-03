package com.agentforge4j.llm.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatRequestDto(
  String model,
  boolean stream,
  List<MessageDto> messages
) {
}
