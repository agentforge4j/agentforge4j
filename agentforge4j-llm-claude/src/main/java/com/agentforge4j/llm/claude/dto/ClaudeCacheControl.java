// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Anthropic {@code cache_control} object for prompt caching breakpoints.
 *
 * @param type cache lifetime type; only {@code ephemeral} is supported by the API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeCacheControl(String type) {

  /**
   * Returns the provider-default ephemeral cache control (no {@code ttl} field).
   *
   * @return {@code {"type":"ephemeral"}}
   */
  public static ClaudeCacheControl ephemeral() {
    return new ClaudeCacheControl("ephemeral");
  }
}
