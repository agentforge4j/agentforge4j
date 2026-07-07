// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmHttpErrorBodyTruncate;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.wireprotocol.ResponsesApiSupport;
import com.agentforge4j.llm.wireprotocol.ResponsesRequest;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/**
 * OpenAI LLM client implementation using the Responses API.
 * <p>
 * Sends requests to OpenAI's API and extracts the assistant's text response. The wire protocol
 * itself (request/response shapes, extraction, validation, and usage mapping) is shared with
 * {@code OpenAiCompatibleLlmClient} via {@link ResponsesApiSupport}, since OpenAI's own Responses
 * API endpoint is itself an OpenAI-compatible one with a fixed URL and bearer auth.
 */
@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class OpenAiLlmClient extends AbstractHttpLlmClient {

  private static final System.Logger LOG = System.getLogger(OpenAiLlmClient.class.getName());
  private static final String PROVIDER_LABEL = "OpenAI";

  private final String apiKey;
  private final ObjectMapper objectMapper;
  private final URI openAiResponsesUri;
  private final Duration requestTimeout;

  /**
   * Creates an OpenAI LLM client with the provided configuration.
   *
   * @param objectMapper the JSON mapper for serialization and deserialization
   * @param config       the OpenAI-specific configuration
   * @throws IllegalArgumentException if required configuration values are missing
   */
  public OpenAiLlmClient(ObjectMapper objectMapper, OpenAiConfiguration config) {
    super(config);
    this.apiKey = Validate.notBlank(config.getApiKey(), "OpenAI apiKey must be provided");
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "OpenAI request timeout must be provided");
    this.objectMapper = Validate.notNull(objectMapper, "OpenAi ObjectMapper must not be null");
    this.openAiResponsesUri = URI.create(
        Validate.notBlank(config.getUrl(), "OpenAI URL must be provided"));
    warnIfApiKeyOverPlainHttp(config.getUrl(), this.apiKey);
  }

  /**
   * Validates the OpenAI Responses API payload and extracts assistant text plus {@code usage}
   * ({@code usage.input_tokens}, {@code usage.output_tokens},
   * {@code usage.input_tokens_details.cached_tokens} when present) and root {@code model} for
   * {@link LlmExecutionResponse#modelUsed()}.
   *
   * @param json the raw JSON response from OpenAI
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when the
   * {@code usage} block is absent
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected LlmExecutionResponse validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be blank"));
    LOG.log(System.Logger.Level.DEBUG, "OpenAI response body (full) body={0}", json);
    String truncatedJson = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(json);
    return ResponsesApiSupport.parseResponse(objectMapper, json, truncatedJson, PROVIDER_LABEL);
  }

  /**
   * Builds the HTTP request for the OpenAI Responses API.
   *
   * @param request the LLM execution request
   * @return the configured HTTP request
   */
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
    ResponsesRequest body = ResponsesApiSupport.buildRequest(
        StringUtils.defaultIfBlank(request.model(), getDefaultModel()),
        request.systemPrompt(),
        request.userInput(),
        request.maxOutputTokens());
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException("Failed to serialize OpenAI request", e);
    }
  }
}
