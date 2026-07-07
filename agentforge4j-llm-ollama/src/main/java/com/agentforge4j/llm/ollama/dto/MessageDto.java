// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.ollama.dto;

import com.agentforge4j.llm.wireprotocol.InputRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageDto(
  InputRole role,
  String content
) {
}
