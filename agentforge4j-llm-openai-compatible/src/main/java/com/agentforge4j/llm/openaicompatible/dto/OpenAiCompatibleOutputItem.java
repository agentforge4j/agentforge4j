// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible.dto;

import java.util.List;

/**
 * Output item from the Responses API.
 */
public record OpenAiCompatibleOutputItem(String type, List<OpenAiCompatibleContentItem> content) {

}
