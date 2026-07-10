// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible;

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
 * OpenAI-compatible LLM client implementation.
 * <p>
 * Sends requests to endpoints that support the OpenAI Responses API format. The wire protocol
 * itself (request/response shapes, extraction, validation, and usage mapping) is shared with
 * {@code OpenAiLlmClient} via {@link ResponsesApiSupport}; only transport concerns (configurable
 * base URL/path and auth header) differ.
 */
@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class OpenAiCompatibleLlmClient extends AbstractHttpLlmClient {

  private static final System.Logger LOG =
      System.getLogger(OpenAiCompatibleLlmClient.class.getName());
  private static final String PROVIDER_LABEL = "openai-compatible";

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
    warnIfApiKeyOverPlainHttp(config.getBaseUrl(), this.apiKey);
  }

  private static URI resolveResponsesUri(OpenAiCompatibleConfiguration config) {
    String base = StringUtils.stripEnd(
        Validate.notBlank(config.getBaseUrl(), "openai-compatible baseUrl must be provided"), "/");
    String path = config.getResponsesPath();
    Validate.notBlank(path, "openai-compatible responsesPath must be provided");
    return URI.create(base + (path.startsWith("/") ? "" : "/") + path);
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
   * Validates the OpenAI-compatible Responses API payload and extracts assistant text plus
   * {@code usage} ({@code usage.input_tokens}, {@code usage.output_tokens},
   * {@code usage.input_tokens_details.cached_tokens} when present) and root {@code model} for
   * {@link LlmExecutionResponse#modelUsed()}.
   *
   * @param json the raw JSON response from the provider
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when the
   * {@code usage} block is absent
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected LlmExecutionResponse validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be blank"));
    LOG.log(System.Logger.Level.DEBUG, "openai-compatible response body (full) body={0}", json);
    String truncatedJson = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(json);
    return ResponsesApiSupport.parseResponse(objectMapper, json, truncatedJson, PROVIDER_LABEL);
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
      throw new LlmInvocationException("Failed to serialize openai-compatible request", e);
    }
  }
}
