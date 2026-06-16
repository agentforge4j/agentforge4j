// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiResponse(
    GeminiErrorResponse error,
    List<GeminiCandidate> candidates,
    @JsonProperty("usageMetadata") GeminiUsageMetadata usageMetadata,
    @JsonProperty("modelVersion") String modelVersion
) {

}
