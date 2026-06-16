// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai.dto;

import java.util.List;

/**
 * Response body for OpenAI responses API.
 *
 * @param error  the error details if failed, or {@code null}
 * @param output the list of output items
 */
public record OpenAiResponsesResponseDto(
    OpenAiErrorDto error,
    List<OpenAiOutputItemDto> output,
    OpenAiResponsesUsageDto usage,
    String model
) {

}
