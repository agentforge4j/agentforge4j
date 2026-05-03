package com.agentforge4j.llm.gemini.dto;

import java.util.List;

public record GeminiRequest(
    GeminiSystemInstruction systemInstruction,
    List<GeminiContent> contents
) {

}
