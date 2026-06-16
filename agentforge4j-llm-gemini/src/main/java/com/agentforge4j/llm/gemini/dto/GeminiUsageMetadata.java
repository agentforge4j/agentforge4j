// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiUsageMetadata(
    @JsonProperty("promptTokenCount") Integer promptTokenCount,
    @JsonProperty("candidatesTokenCount") Integer candidatesTokenCount,
    @JsonProperty("cachedContentTokenCount") Integer cachedContentTokenCount
) {

}
