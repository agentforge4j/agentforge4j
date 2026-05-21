package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;

/**
 * Parses Anthropic Claude message responses returned by Bedrock {@code InvokeModel}.
 */
final class BedrockAnthropicResponseParser {

  /**
   * Parses the InvokeModel response body into assistant text and optional {@code usage}
   * ({@code usage.input_tokens}, {@code usage.output_tokens},
   * {@code usage.cache_read_input_tokens}, {@code usage.cache_creation_input_tokens} when
   * present).
   *
   * @param json         raw InvokeModel response body
   * @param objectMapper JSON mapper
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when the
   * {@code usage} object is absent
   */
  LlmExecutionResponse parse(String json, ObjectMapper objectMapper) throws IOException {
    Validate.notBlank(json,
        () -> new LlmInvocationException("Bedrock response body must not be blank"));
    Validate.notNull(objectMapper, "ObjectMapper must not be null");

    JsonNode root = objectMapper.readTree(json);
    Validate.isTrue(root != null && !root.isNull(), () ->
        new LlmInvocationException("Bedrock response JSON deserialized to null"));

    String text = extractAssistantText(root, json);
    return new LlmExecutionResponse(text, toTokenUsageReport(root.get("usage")));
  }

  private static String extractAssistantText(JsonNode root, String json) {
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
      String blockText = block.path("text").asText(null);
      if (StringUtils.isNotBlank(blockText)) {
        return LlmClient.stripCodeFence(blockText.strip());
      }
    }

    throw new LlmInvocationException("Bedrock Anthropic response has no text content block: %s".
        formatted(json));
  }

  private static TokenUsageReport toTokenUsageReport(JsonNode usage) {
    if (usage == null || usage.isNull() || usage.isMissingNode()) {
      return null;
    }
    return new TokenUsageReport(
        intOrNull(usage, "input_tokens"),
        intOrNull(usage, "output_tokens"),
        intOrNull(usage, "cache_read_input_tokens"),
        intOrNull(usage, "cache_creation_input_tokens"));
  }

  private static Integer intOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || !value.isNumber()) {
      return null;
    }
    return value.intValue();
  }
}
