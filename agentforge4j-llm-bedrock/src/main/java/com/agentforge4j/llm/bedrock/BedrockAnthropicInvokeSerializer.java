package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.bedrock.dto.BedrockSystemContentBlock;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Builds the JSON request body for Bedrock {@code InvokeModel} on Anthropic Claude (messages API).
 */
final class BedrockAnthropicInvokeSerializer {

  static final int DEFAULT_MAX_TOKENS = BedrockInference.DEFAULT_MAX_TOKENS;

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

    int maxTokens = BedrockInference.resolveMaxTokens(request, config);
    root.put("max_tokens", maxTokens);

    Double temperature = config.getTemperature();
    if (temperature != null) {
      root.put("temperature", temperature);
    }

    List<BedrockSystemContentBlock> systemBlocks = BedrockPromptCacheSupport.buildSystemBlocks(
        request.systemPrompt(), request.promptLayerBoundaries(), modelId);
    root.set("system", objectMapper.valueToTree(systemBlocks));

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
}
