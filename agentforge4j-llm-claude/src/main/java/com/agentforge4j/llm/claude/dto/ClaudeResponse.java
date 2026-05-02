package com.agentforge4j.llm.claude.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeResponse(
    List<ClaudeContentBlock> content,
    String error
) {}
