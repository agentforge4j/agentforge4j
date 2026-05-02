package com.agentforge4j.llm.claude.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeContentBlock(String type, String text) {}
