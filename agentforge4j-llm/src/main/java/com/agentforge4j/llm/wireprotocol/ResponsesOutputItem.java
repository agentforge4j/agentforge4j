// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import java.util.List;

/**
 * Output item in a Responses API response.
 *
 * @param type    the output type (e.g., "message")
 * @param content the list of content items
 */
public record ResponsesOutputItem(
    String type,
    List<ResponsesContentItem> content
) {

}
