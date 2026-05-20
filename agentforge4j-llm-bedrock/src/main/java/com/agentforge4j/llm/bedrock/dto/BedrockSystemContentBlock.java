package com.agentforge4j.llm.bedrock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single text block in the Bedrock Anthropic InvokeModel {@code system} array.
 *
 * @param type         content block type ({@code text})
 * @param text         block text
 * @param cacheControl optional cache breakpoint marker; omitted when {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BedrockSystemContentBlock(
    String type,
    String text,
    @JsonProperty("cache_control") BedrockCacheControl cacheControl) {

  /**
   * Creates a plain text system block with no cache marker.
   *
   * @param text block text
   * @return system content block
   */
  public static BedrockSystemContentBlock plainText(String text) {
    return new BedrockSystemContentBlock("text", text, null);
  }

  /**
   * Creates a text system block with an ephemeral cache breakpoint.
   *
   * @param text block text
   * @return system content block with {@code cache_control}
   */
  public static BedrockSystemContentBlock cachedText(String text) {
    return new BedrockSystemContentBlock("text", text, BedrockCacheControl.ephemeral());
  }
}
