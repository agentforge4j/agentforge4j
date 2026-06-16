// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral.dto;

import java.util.List;

/**
 * Response body for Mistral OpenAI-compatible chat completions.
 */
public record MistralChatResponse(
    MistralErrorResponse error,
    List<MistralChoice> choices,
    MistralUsage usage,
    String model
) {

}
