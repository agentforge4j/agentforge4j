// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single text block in the Anthropic Messages API {@code system} array.
 *
 * @param type         content block type ({@code text})
 * @param text         block text
 * @param cacheControl optional cache breakpoint marker; omitted when {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeSystemContentBlock(
    String type,
    String text,
    @JsonProperty("cache_control") ClaudeCacheControl cacheControl) {

  /**
   * Creates a plain text system block with no cache marker.
   *
   * @param text block text
   * @return system content block
   */
  public static ClaudeSystemContentBlock plainText(String text) {
    return new ClaudeSystemContentBlock("text", text, null);
  }

  /**
   * Creates a text system block with an ephemeral cache breakpoint.
   *
   * @param text block text
   * @return system content block with {@code cache_control}
   */
  public static ClaudeSystemContentBlock cachedText(String text) {
    return new ClaudeSystemContentBlock("text", text, ClaudeCacheControl.ephemeral());
  }
}
