// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible.dto;

/**
 * Error payload on a Responses API response.
 */
public record OpenAiCompatibleApiError(String message, String code, String type) {

}
