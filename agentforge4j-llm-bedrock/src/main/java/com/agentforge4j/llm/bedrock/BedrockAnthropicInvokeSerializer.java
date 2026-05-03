package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Builds the JSON request body for Bedrock {@code InvokeModel} on Anthropic Claude (messages API).
 */
final class BedrockAnthropicInvokeSerializer {

  static final int DEFAULT_MAX_TOKENS = 4096;

  private final ObjectMapper objectMapper;

  BedrockAnthropicInvokeSerializer(ObjectMapper objectMapper) {
    this.objectMapper = Validate.notNull(objectMapper, "ObjectMapper must not be null");
  }

  String toJson(LlmExecutionRequest request, String modelId, BedrockConfiguration config) {
    Validate.notNull(request, "request must not be null");
    Validate.notBlank(modelId, "modelId must not be blank");
    Validate.notNull(config, "config must not be null");

    ObjectNode root = objectMapper.createObjectNode();
    root.put("anthropic_version", config.getAnthropicVersion());

    int maxTokens = config.getMaxTokens() != null && config.getMaxTokens() > 0
        ? config.getMaxTokens()
        : DEFAULT_MAX_TOKENS;
    root.put("max_tokens", maxTokens);

    Double temperature = config.getTemperature();
    if (temperature != null) {
      root.put("temperature", temperature);
    }

    root.put("system", request.systemPrompt());

    ArrayNode messages = root.putArray("messages");
    ObjectNode userMessage = messages.addObject();
    userMessage.put("role", "user");
    userMessage.put("content", request.userInput());

    try {
      return objectMapper.writeValueAsString(root);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize Bedrock Anthropic request for model %s".formatted(modelId), e);
    }
  }

  static void validateAnthropicModelId(String modelId) {
    String trimmed = StringUtils.trimToEmpty(modelId);
    Validate.isTrue(Strings.CI.startsWith(trimmed, "anthropic."),
        "Bedrock provider only supports Anthropic Claude model IDs (expected prefix 'anthropic.'), got: %s"
            .formatted(modelId));
  }
}
