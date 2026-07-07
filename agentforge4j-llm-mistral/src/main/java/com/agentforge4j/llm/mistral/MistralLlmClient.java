// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmHttpErrorBodyTruncate;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.wireprotocol.ChatChoice;
import com.agentforge4j.llm.wireprotocol.ChatCompletionsApiSupport;
import com.agentforge4j.llm.wireprotocol.ChatCompletionsRequest;
import com.agentforge4j.llm.wireprotocol.ChatCompletionsResponse;
import com.agentforge4j.llm.wireprotocol.ChatMessage;
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
 * <p>
 * Request/response wire shapes and usage mapping are shared with {@code AzureOpenAiLlmClient} via
 * {@link ChatCompletionsApiSupport}; error/choice validation stays here because Mistral's failure
 * messages omit the deployment-name context that Azure includes.
 */
@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class MistralLlmClient extends AbstractHttpLlmClient {

  private static final System.Logger LOG = System.getLogger(MistralLlmClient.class.getName());

  private final String apiKey;
  private final ObjectMapper objectMapper;
  private final URI chatCompletionsUri;
  private final Duration requestTimeout;

  /**
   * Creates a new Mistral client.
   *
   * @param objectMapper Jackson {@code ObjectMapper} for JSON serialization and deserialization;
   *                     must not be null
   * @param config       Mistral-specific configuration; must not be null
   * @throws IllegalArgumentException if {@code objectMapper} is null, the API key is blank, or the
   *                                  base URL is blank
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
    warnIfApiKeyOverPlainHttp(config.getBaseUrl(), this.apiKey);
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
   * Validates the Mistral chat completions payload and extracts assistant text plus {@code usage}
   * ({@code usage.prompt_tokens}, {@code usage.completion_tokens}) and root {@code model} for
   * {@link LlmExecutionResponse#modelUsed()}.
   *
   * @param json the raw JSON response from Mistral
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when the
   * {@code usage} block is absent
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected LlmExecutionResponse validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be blank"));
    LOG.log(System.Logger.Level.DEBUG, "mistral response body (full) body={0}", json);
    String truncatedJson = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(json);
    ChatCompletionsResponse dto = objectMapper.readValue(json, ChatCompletionsResponse.class);
    ChatChoice firstChoice = validateApiError(truncatedJson, dto);

    ChatMessage message = firstChoice.message();
    String rawContent = message == null ? null : message.content();
    String content = Validate.notBlank(rawContent, () -> new LlmInvocationException(
        "mistral response first choice content is blank: %s".formatted(truncatedJson)));
    return new LlmExecutionResponse(
        LlmClient.stripCodeFence(content.strip()),
        StringUtils.trimToNull(dto.model()),
        ChatCompletionsApiSupport.toTokenUsageReport(dto.usage()));
  }

  private ChatChoice validateApiError(String truncatedJson, ChatCompletionsResponse dto) {
    Validate.notNull(dto,
        () -> new LlmInvocationException(
            "mistral response deserialized to null: %s".formatted(truncatedJson)));
    Validate.isTrue(dto.error() == null || StringUtils.isBlank(dto.error().message()),
        () -> new LlmInvocationException("mistral error: " + dto.error().message()));
    List<ChatChoice> choices =
        Validate.notEmpty(dto.choices(), () -> new LlmInvocationException(
            "mistral response choices are empty: %s".formatted(truncatedJson)));
    ChatChoice firstChoice = choices.get(0);
    return Validate.notNull(firstChoice, () -> new LlmInvocationException(
        "mistral first choice is null: %s".formatted(truncatedJson)));
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    String model = StringUtils.defaultIfBlank(request.model(), getDefaultModel());
    ChatCompletionsRequest body = ChatCompletionsApiSupport.buildRequest(
        model, request.systemPrompt(), request.userInput(), null);
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize mistral request for model %s".formatted(model), e);
    }
  }
}
