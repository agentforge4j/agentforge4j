package com.agentforge4j.llm.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageDto(
  InputRole role,
  String content
) {
}
