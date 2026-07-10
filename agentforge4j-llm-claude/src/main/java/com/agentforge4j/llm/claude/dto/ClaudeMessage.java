// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude.dto;

import com.agentforge4j.llm.wireprotocol.InputRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeMessage(InputRole role, String content) {}
