// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import java.util.List;

/**
 * Response body for an OpenAI-style Responses API.
 *
 * @param error  the error details if failed, or {@code null}
 * @param output the list of output items
 * @param usage  token usage, or {@code null} when the provider omits it
 * @param model  the model that produced the response, when reported
 */
public record ResponsesResponse(
    ApiError error,
    List<ResponsesOutputItem> output,
    ResponsesUsage usage,
    String model
) {

}
