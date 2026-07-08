// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmHttpErrorBodyTruncate;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.llm.ollama.dto.MessageDto;
import com.agentforge4j.llm.ollama.dto.OllamaChatRequestDto;
import com.agentforge4j.llm.ollama.dto.OllamaChatResponseDto;
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

import static com.agentforge4j.llm.wireprotocol.InputRole.SYSTEM;
import static com.agentforge4j.llm.wireprotocol.InputRole.USER;

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
   *                     must not be null
   * @param config       Ollama-specific configuration; must not be null
   * @throws IllegalArgumentException if {@code objectMapper} or {@code config} is null, or if the
   *                                  configured URL is blank
   */
  public OllamaLlmClient(ObjectMapper objectMapper, OllamaConfiguration config) {
    super(config);
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "Ollama request timeout must be provided");
    this.objectMapper = Validate.notNull(objectMapper, "LLM client configuration must not be null");
    this.chatUri = URI.create(Validate.notBlank(config.getUrl(), "Ollama URL must be provided"));
  }

  /**
   * Validates the Ollama chat response and extracts assistant text plus eval counts
   * ({@code prompt_eval_count}, {@code eval_count} when present) and root {@code model} for
   * {@link LlmExecutionResponse#modelUsed()}.
   *
   * @param json the raw JSON response from Ollama
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when
   *         neither eval count field is present
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected LlmExecutionResponse validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be blank"));
    String truncatedJson = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(json);
    OllamaChatResponseDto dto = objectMapper.readValue(json, OllamaChatResponseDto.class);
    validateApiError(dto, truncatedJson);
    return new LlmExecutionResponse(
        LlmClient.stripCodeFence(retrieveResponse(dto, truncatedJson).strip()),
        StringUtils.trimToNull(dto.model()),
        toTokenUsageReport(dto));
  }

  private static TokenUsageReport toTokenUsageReport(OllamaChatResponseDto dto) {
    if (dto.promptEvalCount() == null && dto.evalCount() == null) {
      return null;
    }
    return new TokenUsageReport(dto.promptEvalCount(), dto.evalCount(), null, null);
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

  private static void validateApiError(OllamaChatResponseDto dto, String truncatedJson) {
    Validate.notNull(dto,
        () -> new LlmInvocationException("Ollama response missing: %s".formatted(truncatedJson)));
    if (StringUtils.isNotBlank(dto.error())) {
      throw new LlmInvocationException("Ollama error: %s".formatted(dto.error()));
    }
    Validate.notNull(dto.message(),
        () -> new LlmInvocationException(
            "Ollama response missing message: %s".formatted(truncatedJson)));
  }

  private static String retrieveResponse(OllamaChatResponseDto dto, String truncatedJson) {
    return Validate.notBlank(dto.message().content(),
        () -> new LlmInvocationException(
            "Ollama response has empty message.content: %s".formatted(truncatedJson)));
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
