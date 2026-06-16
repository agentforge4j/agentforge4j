// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible.dto;

import java.util.List;

/**
 * Response body for the OpenAI-compatible Responses API.
 */
public record OpenAiCompatibleResponsesResponse(
    OpenAiCompatibleApiError error,
    List<OpenAiCompatibleOutputItem> output,
    OpenAiCompatibleResponsesUsage usage,
    String model
) {

}
