package com.agentforge4j.llm.ollama;

import static com.agentforge4j.llm.ollama.dto.InputRole.SYSTEM;
import static com.agentforge4j.llm.ollama.dto.InputRole.USER;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.ollama.dto.MessageDto;
import com.agentforge4j.llm.ollama.dto.OllamaChatRequestDto;
import com.agentforge4j.llm.ollama.dto.OllamaChatResponseDto;
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
 * Ollama LLM client using the Ollama REST API (not the gRPC API).
 * <p>
 * See <a href="https://ollama.com/docs/rest-api">Ollama</a> for API details.
 */
@ToString(exclude = {"objectMapper"}, callSuper = true)
public final class OllamaLlmClient extends AbstractHttpLlmClient {

  private final ObjectMapper objectMapper;
  private final URI chatUri;
  private final Duration requestTimeout;

  /**
   * Creates a new Ollama client.
   *
   * @param objectMapper Jackson {@code ObjectMapper} for JSON serialization and deserialization;
   *        must not be null
   * @param config Ollama-specific configuration; must not be null
   * @throws IllegalArgumentException if {@code objectMapper} or {@code config} is null, or if
   *         the configured URL is blank
   */
  public OllamaLlmClient(ObjectMapper objectMapper, OllamaConfiguration config) {
    super(config);
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "Ollama request timeout must be provided");
    this.objectMapper = Validate.notNull(objectMapper, "LLM client configuration must not be null");
    this.chatUri = URI.create(Validate.notBlank(config.getUrl(), "Ollama URL must be provided"));
  }

  /**
   * Validates the Ollama response and extracts the assistant's text output.
   *
   * @param json the raw JSON response from Ollama
   * @return the extracted assistant text
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected String validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be blank"));
    OllamaChatResponseDto dto = objectMapper.readValue(json, OllamaChatResponseDto.class);
    validateApiError(dto, json);
    return stripCodeFence(retrieveResponse(dto, json).strip());
  }

  /**
   * Builds the HTTP request for the Ollama chat API.
   *
   * @param request the LLM execution request
   * @return the configured HTTP request
   */
  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    return HttpRequest.newBuilder(chatUri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(request)))
        .build();
  }

  private static void validateApiError(OllamaChatResponseDto dto, String json) {
    Validate.notNull(dto,
        () -> new LlmInvocationException("Ollama response missing: %s".formatted(json)));
    if (StringUtils.isNotBlank(dto.error())) {
      throw new LlmInvocationException("Ollama error: %s".formatted(dto.error()));
    }
    Validate.notNull(dto.message(),
        () -> new LlmInvocationException("Ollama response missing message: %s".formatted(json)));
  }

  private static String retrieveResponse(OllamaChatResponseDto dto, String json) {
    return Validate.notBlank(dto.message().content(),
        () -> new LlmInvocationException(
            "Ollama response has empty message.content: %s".formatted(json)));
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    OllamaChatRequestDto body = new OllamaChatRequestDto(
        StringUtils.defaultIfBlank(request.model(), getDefaultModel()),
        false,
        List.of(
            new MessageDto(SYSTEM, request.systemPrompt()),
            new MessageDto(USER, request.userInput())));
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException("Failed to serialize Ollama request", e);
    }
  }
}
