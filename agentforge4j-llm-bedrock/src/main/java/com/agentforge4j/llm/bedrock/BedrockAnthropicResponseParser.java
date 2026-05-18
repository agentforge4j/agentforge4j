package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;

/**
 * Parses Anthropic Claude message responses returned by Bedrock {@code InvokeModel}.
 */
final class BedrockAnthropicResponseParser {

  String extractAssistantText(String json, ObjectMapper objectMapper) throws IOException {
    Validate.notBlank(json,
        () -> new LlmInvocationException("Bedrock response body must not be blank"));
    Validate.notNull(objectMapper, "ObjectMapper must not be null");

    JsonNode root = objectMapper.readTree(json);
    Validate.isTrue(root != null && !root.isNull(), () ->
        new LlmInvocationException("Bedrock response JSON deserialized to null"));

    JsonNode content = root.get("content");
    Validate.isTrue(content != null && content.isArray() && !content.isEmpty(), () ->
        new LlmInvocationException(
            "Bedrock Anthropic response missing or empty content array: %s".formatted(json)));

    for (JsonNode block : content) {
      if (block == null || !block.isObject()) {
        continue;
      }
      if (!"text".equalsIgnoreCase(StringUtils.trimToEmpty(block.path("type").asText()))) {
        continue;
      }
      String text = block.path("text").asText(null);
      if (StringUtils.isNotBlank(text)) {
        return LlmClient.stripCodeFence(text.strip());
      }
    }

    throw new LlmInvocationException("Bedrock Anthropic response has no text content block: %s".
        formatted(json));
  }
}
