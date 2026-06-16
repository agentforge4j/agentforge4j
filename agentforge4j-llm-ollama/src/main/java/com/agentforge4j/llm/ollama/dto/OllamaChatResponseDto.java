// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponseDto(
    MessageDto message,
    String error,
    @JsonProperty("prompt_eval_count") Integer promptEvalCount,
    @JsonProperty("eval_count") Integer evalCount,
    String model
) {

}
