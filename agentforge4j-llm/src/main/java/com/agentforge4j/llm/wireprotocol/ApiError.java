// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

/**
 * Error payload embedded in an OpenAI-style API response, shared by both the Responses API and
 * chat-completions API shapes.
 *
 * @param message a human-readable error message
 * @param code    the error code (e.g., "invalid_request_error")
 * @param type    the error type category
 */
public record ApiError(
    String message,
    String code,
    String type
) {

}
