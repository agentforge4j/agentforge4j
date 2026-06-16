// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record GeminiRequest(
    GeminiSystemInstruction systemInstruction,
    List<GeminiContent> contents,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    GeminiGenerationConfig generationConfig
) {

}
