package com.agentforge4j.llm.mistral;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.mistral.dto.InputRole;
import com.agentforge4j.llm.mistral.dto.MistralChatRequest;
import com.agentforge4j.llm.mistral.dto.MistralChatResponse;
import com.agentforge4j.llm.mistral.dto.MistralChoice;
import com.agentforge4j.llm.mistral.dto.MistralMessage;
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
 * Mistral AI LLM client using the OpenAI-compatible chat completions API.
 */
@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class MistralLlmClient extends AbstractHttpLlmClient {

  private final String apiKey;
  private final ObjectMapper objectMapper;
  private final URI chatCompletionsUri;
  private final Duration requestTimeout;

  /**
   * Creates a new Mistral client.
   *
   * @param objectMapper Jackson {@code ObjectMapper} for JSON serialization and deserialization;
   *        must not be null
   * @param config Mistral-specific configuration; must not be null
   * @throws IllegalArgumentException if {@code objectMapper} is null, the API key is blank, or
   *         the base URL is blank
   */
  public MistralLlmClient(ObjectMapper objectMapper, MistralConfiguration config) {
    super(config);
    this.objectMapper = Validate.notNull(objectMapper, "Mistral ObjectMapper must not be null");
    this.apiKey = Validate.notBlank(config.getApiKey(), "Mistral apiKey must be provided");
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "Mistral request timeout must be provided");
    String root = StringUtils.stripEnd(
        Validate.notBlank(config.getBaseUrl(), "Mistral baseUrl must be provided"), "/");
    this.chatCompletionsUri = URI.create(root + "/v1/chat/completions");
  }

  /**
   * Builds the HTTP request for the Mistral chat completions API.
   *
   * @param request the LLM execution request
   * @return the configured HTTP request
   */
  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    return HttpRequest.newBuilder(chatCompletionsUri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(request)))
        .build();
  }

  /**
   * Validates the Mistral response and extracts the assistant's text output.
   *
   * @param json the raw JSON response from Mistral
   * @return the extracted assistant text
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected String validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be blank"));
    MistralChatResponse dto = objectMapper.readValue(json, MistralChatResponse.class);
    MistralChoice firstChoice = validateApiError(json, dto);

    MistralMessage message = firstChoice.message();
    String content = message == null ? null : message.content();
    Validate.notBlank(content, () -> new LlmInvocationException(
        "mistral response first choice content is blank: %s".formatted(json)));
    return LlmClient.stripCodeFence(content.strip());
  }

  private MistralChoice validateApiError(String json, MistralChatResponse dto) {
    Validate.notNull(dto,
        () -> new LlmInvocationException(
            "mistral response deserialized to null: %s".formatted(json)));
    Validate.isTrue(dto.error() == null || StringUtils.isBlank(dto.error().message()),
        () -> new LlmInvocationException("mistral error: " + dto.error().message()));
    List<MistralChoice> choices =
        Validate.notEmpty(dto.choices(), () -> new LlmInvocationException(
            "mistral response choices are empty: %s".formatted(json)));
    MistralChoice firstChoice = choices.get(0);
    return Validate.notNull(firstChoice, () -> new LlmInvocationException(
        "mistral first choice is null: %s".formatted(json)));
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    String model = StringUtils.defaultIfBlank(request.model(), getDefaultModel());
    MistralChatRequest body = new MistralChatRequest(
        model,
        List.of(
            new MistralMessage(InputRole.SYSTEM, request.systemPrompt()),
            new MistralMessage(InputRole.USER, request.userInput())));
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize mistral request for model %s".formatted(model), e);
    }
  }
}
