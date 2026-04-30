package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
import lombok.Getter;

/**
 * Abstract base class for providerName-specific LLM client implementations.
 * <p>
 * This template method implementation handles HTTP communication, request validation, logging, and
 * exception wrapping. Provider-specific subclasses must implement:
 * <ul>
 *   <li>{@link #buildHttpRequest(LlmExecutionRequest)} — construct the providerName-specific HTTP request</li>
 *   <li>{@link #validateAndExtractResponse(String)} — parse and validate the providerName's response JSON</li>
 * </ul>
 * <p>
 * The {@link #execute(LlmExecutionRequest)} method coordinates these steps, logging at debug
 * and error levels for operational visibility.
 */
public abstract class AbstractHttpLlmClient implements LlmClient {

  private static final System.Logger LOG = System.getLogger(AbstractHttpLlmClient.class.getName());

  @Getter
  private final String providerName;
  @Getter
  private final String defaultModel;
  private final HttpClient httpClient;

  /**
   * Constructs an LLM client with the given configuration.
   * <p>
   * Validates that the providerName name and default model are non-blank. Creates an HTTP client
   * configured with the timeout from the configuration.
   *
   * @param config the providerName configuration
   * @throws IllegalArgumentException if the providerName name or default model is blank
   */
  public AbstractHttpLlmClient(LlmClientConfiguration config) {
    validateProvider(config);
    this.providerName = config.getProviderName();
    this.defaultModel = config.getDefaultModel();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(config.getConnectTimeout())
        .build();
  }

  /**
   * Builds a providerName-specific HTTP request from the given execution request.
   *
   * @param request the LLM execution request
   * @return the HTTP request ready to send to the providerName
   */
  protected abstract HttpRequest buildHttpRequest(LlmExecutionRequest request);

  /**
   * Validates and extracts the response body from the providerName's JSON response.
   * <p>
   * Implementations should parse the response JSON, verify success, and extract the actual LLM
   * output.
   *
   * @param json the raw HTTP response body
   * @return the extracted response value
   * @throws IOException if the response is malformed or validation fails
   */
  protected abstract String validateAndExtractResponse(String json) throws IOException;

  /**
   * Executes an LLM request against this providerName.
   * <p>
   * Process:
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
   * @return the extracted LLM response
   * @throws LlmInvocationException   if the request fails due to network issues, HTTP errors, or
   *                                  invalid responses
   * @throws IllegalArgumentException if the request is invalid
   */
  public final String execute(LlmExecutionRequest request) {
    validateRequest(request);
    try {
      HttpResponse<String> response = sendHttpRequest(buildHttpRequest(request));
      return validateAndExtractResponse(requireSuccess(response, providerName));
    } catch (InterruptedException e) {
      handleInterruptedException(e);
    } catch (IOException e) {
      handleIoException(e);
    }
    return null;
  }

  /**
   * Removes markdown code fence markers from the input if present.
   * <p>
   * Strips leading {@code ```} followed by an optional language identifier, and trailing
   * {@code ```}. Returns the input unchanged if it does not start with backticks.
   *
   * @param input the potentially fence-marked string
   * @return the input with fences removed, or the input unchanged
   */
  protected static String stripCodeFence(String input) {
    if (input == null || !input.startsWith("```")) {
      return input;
    }
    int firstNewline = input.indexOf('\n');
    if (firstNewline < 0) {
      return input;
    }
    String afterOpeningFence = input.substring(firstNewline + 1);
    int closingFence = afterOpeningFence.lastIndexOf("```");
    if (closingFence < 0) {
      return afterOpeningFence;
    }
    return afterOpeningFence.substring(0, closingFence).strip();
  }

  private HttpResponse<String> sendHttpRequest(HttpRequest httpRequest)
      throws IOException, InterruptedException {
    LOG.log(System.Logger.Level.DEBUG, "HTTP request dispatched url={0}", httpRequest.uri());
    HttpResponse<String> response = httpClient.send(httpRequest,
        HttpResponse.BodyHandlers.ofString());
    String body = response.body() == null ? "" : response.body();
    LOG.log(System.Logger.Level.DEBUG, "HTTP response received status={0}, bodyCharCount={1}",
        response.statusCode(), body.length());
    return response;
  }

  private void handleInterruptedException(InterruptedException e) {
    Thread.currentThread().interrupt();
    LOG.log(System.Logger.Level.ERROR, "LLM request interrupted providerName={0}", providerName, e);
    throw new LlmInvocationException("%s request interrupted".formatted(providerName), e);
  }

  private void handleIoException(IOException e) {
    LOG.log(System.Logger.Level.ERROR, "LLM request IO failure providerName={0}, message={1}",
        providerName, e.getMessage(), e);
    throw new LlmInvocationException("%s request failed".formatted(providerName), e);
  }

  private void validateProvider(LlmClientConfiguration config) {
    Validate.notNull(config, "LLM client configuration must not be null");
    Validate.notBlank(config.getProviderName(),
        "Provider name must be provided in the configuration");
    Validate.notBlank(config.getDefaultModel(),
        "%s default model must be provided".formatted(config.getProviderName()));
  }

  private void validateRequest(LlmExecutionRequest request) {
    Validate.notNull(request, "Request must not be null");
    Validate.notBlank(request.providerName(), "Request providerName must be specified");
    Validate.isTrue(
        providerName.equalsIgnoreCase(request.providerName()),
        "Request providerName '%s' does not match client providerName '%s'".formatted(
            request.providerName(),
            providerName
        )
    );
    Validate.notBlank(request.userInput(), "Request user input must be provided");
    Validate.notBlank(request.systemPrompt(), "Request system prompt must be provided");
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
      String truncated = body == null ? "" : body.substring(0, Math.min(500, body.length()));
      LOG.log(System.Logger.Level.ERROR, "Non-2xx response providerName={0}, status={1}, body={2}",
          providerName, status, truncated);
      return new LlmInvocationException(
          "%s HTTP error: %s - %s".formatted(providerName, status, body));
    };
  }
}
