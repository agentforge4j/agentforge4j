// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

/**
 * Input item for the OpenAI-style Responses API.
 *
 * @param role    the role of the input
 * @param content the input content
 */
public record ResponsesInputItem(
    InputRole role,
    String content
) {

}
