// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeRequest(
    String model,
    @JsonProperty("max_tokens") int maxTokens,
    List<ClaudeSystemContentBlock> system,
    List<ClaudeMessage> messages
) {}
