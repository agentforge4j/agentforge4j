package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.claude.dto.ClaudeContentBlock;
import com.agentforge4j.llm.claude.dto.ClaudeMessage;
import com.agentforge4j.llm.claude.dto.ClaudeRequest;
import com.agentforge4j.llm.claude.dto.ClaudeResponse;
import com.agentforge4j.llm.claude.dto.InputRole;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class ClaudeLlmClient extends AbstractHttpLlmClient {

  private final String apiKey;
  private final String apiVersion;
  private final ObjectMapper objectMapper;
  private final URI messagesUri;
  private final Duration requestTimeout;
  private final int maxTokenSize;

  public ClaudeLlmClient(ObjectMapper objectMapper, ClaudeConfiguration config) {
    super(config);
    this.apiKey = Validate.notBlank(config.getApiKey(), "Claude apiKey must be provided");
    this.apiVersion = Validate.notBlank(config.getApiVersion(),
        "Claude apiVersion must be provided");
    this.requestTimeout = config.getRequestTimeout();
    this.objectMapper = Validate.notNull(objectMapper, "Claude ObjectMapper must not be null");
    this.messagesUri = URI.create(
        Validate.notBlank(config.getUrl(), "Claude URL must be provided"));
    this.maxTokenSize = (int) Validate.isGreaterThanZero(config.getMaxTokenSize(),
        "Claude maxTokenSize must be positive");
  }

  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    return HttpRequest.newBuilder(messagesUri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .header("x-api-key", apiKey)
        .header("anthropic-version", apiVersion)
        .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(request)))
        .build();
  }

  @Override
  protected String validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be null"));
    ClaudeResponse response = objectMapper.readValue(json, ClaudeResponse.class);
    Validate.notNull(response, () -> new LlmInvocationException(
        "Claude response deserialized to null for model %s: %s".formatted(
            getDefaultModel(), json)));

    List<ClaudeContentBlock> content = Validate.notEmpty(response.content(),
        () -> new LlmInvocationException(
            "Claude response content is empty: %s".formatted(json)));

    String text = content.stream()
        .filter(block -> block != null && "text".equals(block.type()))
        .map(ClaudeContentBlock::text)
        .filter(StringUtils::isNotBlank)
        .findFirst()
        .orElseThrow(() -> new LlmInvocationException(
            "Claude response has no text content block: %s".formatted(json)));

    return stripCodeFence(text.strip());
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    String model = StringUtils.defaultIfBlank(getDefaultModel(), request.model());
    ClaudeRequest body = new ClaudeRequest(
        model,
        maxTokenSize,
        request.systemPrompt(),
        List.of(new ClaudeMessage(InputRole.USER, request.userInput())));
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize Claude request for model %s".formatted(model), e);
    }
  }
}
