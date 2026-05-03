package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.openaicompatible.dto.InputRole;
import com.agentforge4j.llm.openaicompatible.dto.OpenAiCompatibleContentItem;
import com.agentforge4j.llm.openaicompatible.dto.OpenAiCompatibleInputItem;
import com.agentforge4j.llm.openaicompatible.dto.OpenAiCompatibleOutputItem;
import com.agentforge4j.llm.openaicompatible.dto.OpenAiCompatibleResponsesRequest;
import com.agentforge4j.llm.openaicompatible.dto.OpenAiCompatibleResponsesResponse;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * OpenAI-compatible LLM client implementation.
 * <p>
 * Sends requests to endpoints that support the OpenAI Responses API format.
 */
@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class OpenAiCompatibleLlmClient extends AbstractHttpLlmClient {

  private final String apiKey;
  private final ObjectMapper objectMapper;
  private final URI responsesUri;
  private final Duration requestTimeout;
  private final String authHeaderName;
  private final String authHeaderPrefix;

  /**
   * Creates an OpenAI-compatible LLM client with the provided configuration.
   *
   * @param objectMapper the JSON mapper for serialization and deserialization
   * @param config       the OpenAI-compatible-specific configuration
   * @throws IllegalArgumentException if required configuration values are missing
   */
  public OpenAiCompatibleLlmClient(ObjectMapper objectMapper,
      OpenAiCompatibleConfiguration config) {
    super(config);
    this.apiKey = Validate.notBlank(config.getApiKey(),
        "openai-compatible apiKey must be provided");
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "openai-compatible request timeout must be provided");
    this.objectMapper = Validate.notNull(objectMapper,
        "openai-compatible ObjectMapper must not be null");
    this.responsesUri = resolveResponsesUri(config);
    this.authHeaderName = Validate.notBlank(config.getAuthHeaderName(),
        "openai-compatible authHeaderName must be provided");
    this.authHeaderPrefix = Validate.notNull(config.getAuthHeaderPrefix(),
        "openai-compatible authHeaderPrefix must not be null");
  }

  private static URI resolveResponsesUri(OpenAiCompatibleConfiguration config) {
    String base = StringUtils.stripEnd(
        Validate.notBlank(config.getBaseUrl(), "openai-compatible baseUrl must be provided"), "/");
    String path = config.getResponsesPath();
    Validate.notBlank(path, "openai-compatible responsesPath must be provided");
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return URI.create(base + path);
  }

  /**
   * Builds the HTTP request for the OpenAI-compatible Responses API.
   *
   * @param request the LLM execution request
   * @return the configured HTTP request
   */
  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    String headerValue = authHeaderPrefix + apiKey;
    return HttpRequest.newBuilder(responsesUri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .header(authHeaderName, headerValue)
        .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(request)))
        .build();
  }

  /**
   * Validates the OpenAI-compatible response and extracts the assistant's text output.
   *
   * @param json the raw JSON response from the provider
   * @return the extracted assistant text
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected String validateAndExtractResponse(String json) throws IOException {
    OpenAiCompatibleResponsesResponse dto =
        objectMapper.readValue(json, OpenAiCompatibleResponsesResponse.class);
    validateApiError(dto, json);
    return stripCodeFence(extractAssistantText(dto)
        .orElseThrow(
            () -> {
              String truncated = json.substring(0, Math.min(500, json.length()));
              return new LlmInvocationException(
                  "openai-compatible response missing assistant output_text in message output item: %s".formatted(
                      truncated));
            }
        ).strip());
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    OpenAiCompatibleResponsesRequest body = new OpenAiCompatibleResponsesRequest(
        StringUtils.defaultIfBlank(request.model(), getDefaultModel()),
        List.of(
            new OpenAiCompatibleInputItem(InputRole.SYSTEM, request.systemPrompt()),
            new OpenAiCompatibleInputItem(InputRole.USER, request.userInput())),
        request.maxOutputTokens());
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException("Failed to serialize openai-compatible request", e);
    }
  }

  private static void validateApiError(OpenAiCompatibleResponsesResponse dto, String rawJson) {
    Validate.notNull(dto, () -> new LlmInvocationException(
        "openai-compatible response deserialized to null: " + rawJson));
    Validate.isTrue(dto.error() == null || StringUtils.isBlank(dto.error().message()),
        () -> new LlmInvocationException("openai-compatible error: " + dto.error().message()));
    Validate.isTrue(dto.output() != null && !dto.output().isEmpty(),
        () -> new LlmInvocationException(
            "openai-compatible response missing or empty output: " + rawJson));
  }

  private static Optional<String> extractAssistantText(OpenAiCompatibleResponsesResponse dto) {
    return dto.output().stream()
        .filter(item -> item != null && "message".equalsIgnoreCase(item.type()))
        .map(OpenAiCompatibleOutputItem::content)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .filter(content -> content != null && "output_text".equalsIgnoreCase(content.type()))
        .map(OpenAiCompatibleContentItem::text)
        .filter(StringUtils::isNotBlank)
        .findFirst();
  }
}
