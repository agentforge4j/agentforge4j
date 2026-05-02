package com.agentforge4j.llm.claude.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeRequest(
    String model,
    @JsonProperty("max_tokens") int maxTokens,
    String system,
    List<ClaudeMessage> messages
) {}
