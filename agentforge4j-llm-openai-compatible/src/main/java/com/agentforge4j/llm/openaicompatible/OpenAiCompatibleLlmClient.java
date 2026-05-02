package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.openai.dto.InputItem;
import com.agentforge4j.llm.openai.dto.OpenAiContentItemDto;
import com.agentforge4j.llm.openai.dto.OpenAiOutputItemDto;
import com.agentforge4j.llm.openai.dto.OpenAiResponsesRequestDto;
import com.agentforge4j.llm.openai.dto.OpenAiResponsesResponseDto;
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

import static com.agentforge4j.llm.openai.dto.InputRole.SYSTEM;
import static com.agentforge4j.llm.openai.dto.InputRole.USER;

@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class OpenAiCompatibleLlmClient extends AbstractHttpLlmClient {

  private final String apiKey;
  private final ObjectMapper objectMapper;
  private final URI responsesUri;
  private final Duration requestTimeout;
  private final String authHeaderName;
  private final String authHeaderPrefix;

  public OpenAiCompatibleLlmClient(ObjectMapper objectMapper,
      OpenAiCompatibleConfiguration config) {
    super(config);
    this.apiKey = Validate.notBlank(config.getApiKey(),
        "openai-compatible apiKey must be provided");
    this.requestTimeout = config.getRequestTimeout();
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

  @Override
  protected String validateAndExtractResponse(String json) throws IOException {
    OpenAiResponsesResponseDto dto = objectMapper.readValue(json, OpenAiResponsesResponseDto.class);
    validateApiError(dto, json);
    return extractAssistantText(dto)
        .orElseThrow(
            () -> new LlmInvocationException(
                "openai-compatible response missing assistant output_text in message output item: "
                    + json));
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    OpenAiResponsesRequestDto body = new OpenAiResponsesRequestDto(
        StringUtils.defaultIfBlank(request.model(), getDefaultModel()),
        List.of(
            new InputItem(SYSTEM, request.systemPrompt()),
            new InputItem(USER, request.userInput())));
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException("Failed to serialize openai-compatible request", e);
    }
  }

  private static void validateApiError(OpenAiResponsesResponseDto dto, String rawJson) {
    Validate.notNull(dto, () -> new LlmInvocationException(
        "openai-compatible response deserialized to null: " + rawJson));
    Validate.isTrue(dto.error() == null || StringUtils.isBlank(dto.error().message()),
        () -> new LlmInvocationException("openai-compatible error: " + dto.error().message()));
    Validate.isTrue(dto.output() != null && !dto.output().isEmpty(),
        () -> new LlmInvocationException(
            "openai-compatible response missing or empty output: " + rawJson));
  }

  private static Optional<String> extractAssistantText(OpenAiResponsesResponseDto dto) {
    return dto.output().stream()
        .filter(item -> item != null && "message".equalsIgnoreCase(item.type()))
        .map(OpenAiOutputItemDto::content)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .filter(content -> content != null && "output_text".equalsIgnoreCase(content.type()))
        .map(OpenAiContentItemDto::text)
        .filter(StringUtils::isNotBlank)
        .findFirst();
  }
}
