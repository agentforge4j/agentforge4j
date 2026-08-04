// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmHttpErrorBodyTruncate;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.wireprotocol.ChatChoice;
import com.agentforge4j.llm.wireprotocol.ChatCompletionsApiSupport;
import com.agentforge4j.llm.wireprotocol.ChatCompletionsRequest;
import com.agentforge4j.llm.wireprotocol.ChatCompletionsResponse;
import com.agentforge4j.llm.wireprotocol.ChatMessage;
import com.agentforge4j.util.Validate;
import com.agentforge4j.util.text.CodeFence;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * vLLM LLM client implementation.
 * <p>
 * Sends requests to a vLLM server using the chat completions API. Request/response wire shapes and
 * usage mapping are shared with {@code AzureOpenAiLlmClient} and {@code MistralLlmClient} via
 * {@link ChatCompletionsApiSupport}; vLLM sends an explicit {@code "stream": false} where the
 * other two omit the field. Unlike those two, vLLM performs no API-error check &mdash; a failed
 * call surfaces as an empty {@code choices} array.
 */
public final class VllmLlmClient extends AbstractHttpLlmClient {

  private static final System.Logger LOG = System.getLogger(VllmLlmClient.class.getName());

  private final ObjectMapper objectMapper;
  private final URI chatCompletionsUri;
  private final Duration requestTimeout;

  /**
   * Creates a vLLM LLM client with the provided configuration.
   *
   * @param objectMapper the JSON mapper for serialization and deserialization
   * @param config       the vLLM-specific configuration
   * @throws IllegalArgumentException if required configuration values are missing
   */
  public VllmLlmClient(ObjectMapper objectMapper, VllmConfiguration config) {
    super(config);
    this.objectMapper = Validate.notNull(objectMapper, "vLLM ObjectMapper must not be null");
    this.chatCompletionsUri = URI.create(
        Validate.notBlank(config.getUrl(), "vLLM URL must be provided"));
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "vLLM request timeout must be provided");
  }

  /**
   * Builds the HTTP request for the vLLM chat completions API.
   *
   * @param request the LLM execution request
   * @return the configured HTTP request
   */
  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    return HttpRequest.newBuilder(chatCompletionsUri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(request)))
        .build();
  }

  /**
   * Validates the vLLM OpenAI-compatible chat completions payload and extracts assistant text plus
   * {@code usage} ({@code usage.prompt_tokens}, {@code usage.completion_tokens},
   * {@code usage.prompt_tokens_details.cached_tokens} when present) and root {@code model} for
   * {@link LlmExecutionResponse#modelUsed()}.
   *
   * @param json the raw JSON response from vLLM
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when the
   * {@code usage} block is absent
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected LlmExecutionResponse validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException(
        "%s response body must not be blank".formatted(getProviderName())));
    LOG.log(System.Logger.Level.DEBUG, "vLLM response body (full) body={0}", json);
    String truncatedJson = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(json);
    ChatCompletionsResponse response = objectMapper.readValue(json, ChatCompletionsResponse.class);
    List<ChatChoice> choices = Validate.notEmpty(
        response == null ? null : response.choices(),
        () -> new LlmInvocationException(
            "vLLM response choices are empty: %s".formatted(truncatedJson)));

    ChatChoice firstChoice = choices.get(0);
    ChatMessage message = firstChoice == null ? null : firstChoice.message();
    String rawContent = message == null ? null : message.content();
    String content = Validate.notBlank(rawContent, () -> new LlmInvocationException(
        "vLLM response first choice content is blank: %s".formatted(truncatedJson)));

    String modelUsed = response == null ? null : response.model();
    return new LlmExecutionResponse(
        CodeFence.strip(content.strip()),
        StringUtils.trimToNull(modelUsed),
        ChatCompletionsApiSupport.toTokenUsageReport(response == null ? null : response.usage()));
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    String model = StringUtils.defaultIfBlank(request.model(), getDefaultModel());
    ChatCompletionsRequest body = ChatCompletionsApiSupport.buildRequest(
        model, request.systemPrompt(), request.userInput(), Boolean.FALSE);
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize vLLM request for model %s".formatted(model), e);
    }
  }
}
