// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible.dto;

/**
 * One input entry for the OpenAI-compatible Responses API.
 */
public record OpenAiCompatibleInputItem(InputRole role, String content) {

}
