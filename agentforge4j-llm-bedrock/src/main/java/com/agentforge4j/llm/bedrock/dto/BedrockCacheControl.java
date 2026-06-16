// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Anthropic-on-Bedrock {@code cache_control} object for InvokeModel prompt caching breakpoints.
 *
 * @param type cache lifetime type; only {@code ephemeral} is supported for this path
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BedrockCacheControl(String type) {

  /**
   * Returns the provider-default ephemeral cache control (no {@code ttl} field).
   *
   * @return {@code {"type":"ephemeral"}}
   */
  public static BedrockCacheControl ephemeral() {
    return new BedrockCacheControl("ephemeral");
  }
}
