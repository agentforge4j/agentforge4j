// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VllmMessageContent(String content) {

}
