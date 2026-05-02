package com.agentforge4j.llm.openai;

import static com.agentforge4j.llm.openai.dto.InputRole.SYSTEM;
import static com.agentforge4j.llm.openai.dto.InputRole.USER;

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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class OpenAiLlmClient extends AbstractHttpLlmClient {

  private final String apiKey;
  private final ObjectMapper objectMapper;
  private final URI openAiResponsesUri;
  private final Duration requestTimeout;

  public OpenAiLlmClient(ObjectMapper objectMapper, OpenAiConfiguration config) {
    super(config);
    this.apiKey = Validate.notBlank(config.getApiKey(), "OpenAI apiKey must be provided");
    this.requestTimeout = config.getRequestTimeout();
    this.objectMapper = Validate.notNull(objectMapper, "OpenAi ObjectMapper must not be null");
    this.openAiResponsesUri = URI.create(
        Validate.notBlank(config.getUrl(), "OpenAI URL must be provided"));
  }

  @Override
  protected String validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be null"));
    OpenAiResponsesResponseDto dto = objectMapper.readValue(json, OpenAiResponsesResponseDto.class);
    validateApiError(dto, json);
    return extractAssistantText(dto)
        .orElseThrow(() -> new LlmInvocationException(
            "OpenAI response missing assistant output_text in message output item: %s".formatted(
                json)));
  }

  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    return HttpRequest.newBuilder(openAiResponsesUri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(request)))
        .build();
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
      throw new LlmInvocationException("Failed to serialize OpenAI request", e);
    }
  }

  private static void validateApiError(OpenAiResponsesResponseDto dto, String rawJson) {
    Validate.notNull(dto,
        () -> new LlmInvocationException(
            "OpenAI response deserialized to null: %s".formatted(rawJson)));
    Validate.isTrue(dto.error() == null || StringUtils.isBlank(dto.error().message()),
        () -> new LlmInvocationException("OpenAI error: " + dto.error().message()));
    Validate.isTrue(dto.output() != null && !dto.output().isEmpty(),
        () -> new LlmInvocationException("OpenAI response missing or empty output: " + rawJson));
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
