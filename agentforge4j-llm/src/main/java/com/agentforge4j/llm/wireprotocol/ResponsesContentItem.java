// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

/**
 * Content item in a Responses API output item.
 *
 * @param type the content type (e.g., "output_text")
 * @param text the text content
 */
public record ResponsesContentItem(
    String type,
    String text
) {

}
