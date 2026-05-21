package com.agentforge4j.llm.vllm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VllmResponse(List<VllmChoice> choices, VllmUsage usage) {

}
