package com.agentforge4j.llm.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Gemini {@code generateContent} generation settings.
 *
 * @param maxOutputTokens maximum tokens to generate; omitted from JSON when null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiGenerationConfig(Integer maxOutputTokens) {

}
