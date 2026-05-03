package com.agentforge4j.llm.vllm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VllmMessage(InputRole role, String content) {

}
