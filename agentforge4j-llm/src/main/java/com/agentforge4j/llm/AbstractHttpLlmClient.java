// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Base {@link LlmClient} for HTTP JSON APIs: shared validation, transport, logging, and error
 * wrapping.
 * <p>
 * Subclasses implement:
 * <ul>
 *   <li>{@link #buildHttpRequest(LlmExecutionRequest)} — vendor-specific request shape and headers</li>
 *   <li>{@link #validateAndExtractResponse(String)} — parse success responses into text and optional token usage</li>
 * </ul>
 * <p>
 * {@link #execute(LlmExecutionRequest)} orchestrates build, send, status check, and extraction.
 */
public abstract class AbstractHttpLlmClient implements LlmClient {

  private static final System.Logger LOG = System.getLogger(AbstractHttpLlmClient.class.getName());
  private static final int MAX_ERROR_BODY_MESSAGE_CHARS = 500;

  @Getter
  private final String providerName;
  @Getter
  private final String defaultModel;
  private final LlmClientConfiguration clientConfiguration;
  private final HttpClient httpClient;

  /**
   * Constructs an HTTP-backed client from configuration (provider id, default model, connect
   * timeout).
   *
   * @param config non-null provider configuration
   *
   * @throws IllegalArgumentException if provider id or default model is blank
   */
  public AbstractHttpLlmClient(LlmClientConfiguration config) {
    Validate.notNull(config, "LLM client configuration must not be null");
    this.providerName = Validate.notBlank(config.getProviderName(),
        "Provider name must be provided in the configuration");
    this.defaultModel = Validate.notBlank(config.getDefaultModel(),
        "%s default model must be provided".formatted(config.getProviderName()));
    this.clientConfiguration = config;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(config.getConnectTimeout())
        .build();
  }

  @Override
  public LlmRetryPolicy getRetryPolicy() {
    return clientConfiguration.getRetryPolicy();
  }

  /**
   * Logs a warning when an API key is configured together with a plain {@code http://} endpoint,
   * because the key would be sent unencrypted. Local key-less deployments (Ollama, vLLM) never
   * trigger this. Call from provider constructors after URL and key are resolved.
   *
   * @param url    the configured endpoint URL; no warning when {@code null} or blank
   * @param apiKey the configured API key; no warning when {@code null} or blank
   */
  protected final void warnIfApiKeyOverPlainHttp(String url, String apiKey) {
    if (StringUtils.isNotBlank(apiKey)
        && Strings.CI.startsWith(StringUtils.trimToEmpty(url), "http://")) {
      LOG.log(System.Logger.Level.WARNING,
          "{0} is configured with an API key over plain http; the key will be sent unencrypted"
              + " - use https", providerName);
    }
  }

  /**
   * Builds the outbound HTTP request for one execution.
   *
   * @param request validated execution parameters
   *
   * @return request ready to send
   */
  protected abstract HttpRequest buildHttpRequest(LlmExecutionRequest request);

  /**
   * Parses a successful HTTP body into model output and optional provider token usage.
   *
   * @param json raw HTTP response body
   *
   * @return execution response with extracted text; {@link LlmExecutionResponse#tokenUsage()} is
   * {@code null} when the provider returned no usage block
   *
   * @throws IOException if the body is malformed or indicates failure
   */
  protected abstract LlmExecutionResponse validateAndExtractResponse(String json)
      throws IOException;

  /**
   * Sends one LLM request for this client's provider.
   * <p>
   * Steps:
   * <ol>
   *   <li>Validates request parameters</li>
   *   <li>Resolves model (uses request model or falls back to default)</li>
   *   <li>Builds the HTTP request via {@link #buildHttpRequest(LlmExecutionRequest)}</li>
   *   <li>Sends the request and handles the response</li>
   *   <li>Validates HTTP status code is 2xx</li>
   *   <li>Extracts and returns the response via {@link #validateAndExtractResponse(String)}</li>
   * </ol>
   *
   * @param request the LLM execution request
   *
   * @return execution response with extracted model output and provider token usage when reported
   *
   * @throws LlmInvocationException   if the request fails due to network issues, HTTP errors, or
   *                                  invalid responses
   * @throws IllegalArgumentException if the request is invalid
   */
  public final LlmExecutionResponse execute(LlmExecutionRequest request) {
    LlmExecutionRequestValidator.validate(request, providerName);
    try {
      HttpResponse<String> response = sendHttpRequest(buildHttpRequest(request));
      return validateAndExtractResponse(requireSuccess(response, providerName));
    } catch (InterruptedException e) {
      throw handleInterruptedException(e);
    } catch (IOException e) {
      throw handleIoException(e);
    }
  }

  private HttpResponse<String> sendHttpRequest(HttpRequest httpRequest)
      throws IOException, InterruptedException {
    LOG.log(System.Logger.Level.DEBUG, "HTTP request dispatched url={0}", httpRequest.uri());
    HttpResponse<String> response = httpClient.send(httpRequest,
        HttpResponse.BodyHandlers.ofString());
    String body = StringUtils.defaultString(response.body());
    LOG.log(System.Logger.Level.DEBUG, "HTTP response received status={0}, bodyCharCount={1}",
        response.statusCode(), body.length());
    return response;
  }

  private LlmInvocationException handleInterruptedException(InterruptedException e) {
    Thread.currentThread().interrupt();
    LOG.log(System.Logger.Level.ERROR, "LLM request interrupted providerName={0}", providerName, e);
    return new LlmInvocationException("%s request interrupted".formatted(providerName), e);
  }

  private LlmInvocationException handleIoException(IOException e) {
    LOG.log(System.Logger.Level.ERROR, "LLM request IO failure providerName={0}, message={1}",
        providerName, e.getMessage(), e);
    return new LlmInvocationException("%s request failed".formatted(providerName), e);
  }

  private String requireSuccess(HttpResponse<String> response, String providerName) {
    int status = response.statusCode();
    String body = response.body();
    Validate.isBetween(200, 299, status, requireSuccessException(body, status, providerName));
    return body;
  }

  private Supplier<RuntimeException> requireSuccessException(String body, int status,
      String providerName) {
    return () -> {
      String fullBody = StringUtils.defaultString(body);
      String truncated = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(fullBody,
          MAX_ERROR_BODY_MESSAGE_CHARS);
      LOG.log(System.Logger.Level.ERROR, "Non-2xx response providerName={0}, status={1}, body={2}",
          providerName, status, truncated);
      LOG.log(System.Logger.Level.DEBUG,
          "Non-2xx response full body providerName={0}, status={1}, body={2}",
          providerName, status, fullBody);
      return new LlmInvocationException(
          "%s HTTP error: %s - %s".formatted(providerName, status, truncated), status);
    };
  }
}
