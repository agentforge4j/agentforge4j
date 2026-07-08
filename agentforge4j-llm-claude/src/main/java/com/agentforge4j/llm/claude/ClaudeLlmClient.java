// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmHttpErrorBodyTruncate;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.llm.claude.dto.ClaudeContentBlock;
import com.agentforge4j.llm.claude.dto.ClaudeMessage;
import com.agentforge4j.llm.claude.dto.ClaudeRequest;
import com.agentforge4j.llm.claude.dto.ClaudeResponse;
import com.agentforge4j.llm.claude.dto.ClaudeSystemContentBlock;
import com.agentforge4j.llm.claude.dto.ClaudeUsage;
import com.agentforge4j.llm.wireprotocol.InputRole;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/**
 * Anthropic Claude LLM client implementation.
 * <p>
 * Sends requests to Claude's Messages API and extracts the assistant's text response.
 */
@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class ClaudeLlmClient extends AbstractHttpLlmClient {

  private final String apiKey;
  private final String apiVersion;
  private final ObjectMapper objectMapper;
  private final URI messagesUri;
  private final Duration requestTimeout;
  private final int maxTokenSize;

  /**
   * Creates a Claude LLM client with the provided configuration.
   *
   * @param objectMapper the JSON mapper for serialization and deserialization
   * @param config       the Claude-specific configuration
   * @throws IllegalArgumentException if required configuration values are missing
   */
  public ClaudeLlmClient(ObjectMapper objectMapper, ClaudeConfiguration config) {
    super(config);
    this.apiKey = Validate.notBlank(config.getApiKey(), "Claude apiKey must be provided");
    this.apiVersion = Validate.notBlank(config.getApiVersion(),
        "Claude apiVersion must be provided");
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "Claude request timeout must be provided");
    this.objectMapper = Validate.notNull(objectMapper, "Claude ObjectMapper must not be null");
    this.messagesUri = URI.create(
        Validate.notBlank(config.getUrl(), "Claude URL must be provided"));
    this.maxTokenSize = Validate.isGreaterThanZero(config.getMaxTokenSize(),
        "Claude maxTokenSize must be positive").intValue();
    warnIfApiKeyOverPlainHttp(config.getUrl(), this.apiKey);
  }

  /**
   * Builds the HTTP request for the Claude Messages API.
   *
   * @param request the LLM execution request
   * @return the configured HTTP request
   */
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

  /**
   * Validates the Claude Messages API payload and extracts assistant text plus {@code usage}
   * ({@code usage.input_tokens}, {@code usage.output_tokens},
   * {@code usage.cache_read_input_tokens}, {@code usage.cache_creation_input_tokens} when
   * present) and root {@code model} for {@link LlmExecutionResponse#modelUsed()}.
   *
   * @param json the raw JSON response from Claude
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when the
   * {@code usage} block is absent
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected LlmExecutionResponse validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be blank"));
    String truncatedJson = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(json);
    ClaudeResponse response = objectMapper.readValue(json, ClaudeResponse.class);
    Validate.notNull(response, () -> new LlmInvocationException(
        "Claude response deserialized to null for model %s: %s".formatted(
            getDefaultModel(), truncatedJson)));

    List<ClaudeContentBlock> content = Validate.notEmpty(response.content(),
        () -> new LlmInvocationException(
            "Claude response content is empty: %s".formatted(truncatedJson)));

    String text = content.stream()
        .filter(block -> block != null && "text".equals(block.type()))
        .map(ClaudeContentBlock::text)
        .filter(StringUtils::isNotBlank)
        .findFirst()
        .orElseThrow(() -> new LlmInvocationException(
            "Claude response has no text content block: %s".formatted(truncatedJson)));

    return new LlmExecutionResponse(
        LlmClient.stripCodeFence(text.strip()),
        StringUtils.trimToNull(response.model()),
        toTokenUsageReport(response.usage()));
  }

  private static TokenUsageReport toTokenUsageReport(ClaudeUsage usage) {
    if (usage == null) {
      return null;
    }
    return new TokenUsageReport(
        usage.inputTokens(),
        usage.outputTokens(),
        usage.cacheReadInputTokens(),
        usage.cacheCreationInputTokens());
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    String model = StringUtils.defaultIfBlank(request.model(), getDefaultModel());
    List<ClaudeSystemContentBlock> systemBlocks = ClaudePromptCacheSupport.buildSystemBlocks(
        request.systemPrompt(), request.promptLayerBoundaries(), model);
    ClaudeRequest body = new ClaudeRequest(
        model,
        maxTokenSize,
        systemBlocks,
        List.of(new ClaudeMessage(InputRole.USER, request.userInput())));
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize Claude request for model %s".formatted(model), e);
    }
  }
}
