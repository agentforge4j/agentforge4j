// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini.dto;

import com.agentforge4j.llm.wireprotocol.InputRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiContent(InputRole role, List<GeminiPart> parts) {

}
