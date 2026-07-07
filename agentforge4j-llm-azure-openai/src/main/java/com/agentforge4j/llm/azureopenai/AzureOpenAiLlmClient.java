// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai;

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
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/**
 * Azure OpenAI LLM client implementation.
 * <p>
 * Sends requests to Azure OpenAI using the chat completions API. Request/response wire shapes and
 * usage mapping are shared with {@code MistralLlmClient} and {@code VllmLlmClient} via
 * {@link ChatCompletionsApiSupport}; error/choice validation stays here because Azure's failure
 * messages embed the deployment name, unlike the other two providers.
 */
@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class AzureOpenAiLlmClient extends AbstractHttpLlmClient {

  private static final System.Logger LOG =
      System.getLogger(AzureOpenAiLlmClient.class.getName());

  private final String apiKey;
  private final String deploymentName;
  private final ObjectMapper objectMapper;
  private final URI chatCompletionsUri;
  private final Duration requestTimeout;

  /**
   * Creates an Azure OpenAI LLM client with the provided configuration.
   *
   * @param objectMapper the JSON mapper for serialization and deserialization
   * @param config       the Azure OpenAI-specific configuration
   * @throws IllegalArgumentException if required configuration values are missing
   */
  public AzureOpenAiLlmClient(ObjectMapper objectMapper, AzureOpenAiConfiguration config) {
    super(config);
    Validate.notBlank(config.getEndpoint(), "Azure OpenAI endpoint must be provided");
    Validate.notBlank(config.getApiVersion(), "Azure OpenAI apiVersion must be provided");
    this.objectMapper = Validate.notNull(objectMapper,
        "Azure OpenAI ObjectMapper must not be null");
    this.apiKey = Validate.notBlank(config.getApiKey(), "Azure OpenAI apiKey must be provided");
    this.deploymentName = Validate.notBlank(config.getDeploymentName(),
        "Azure OpenAI deploymentName must be provided");
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "Azure openAi request timeout must be provided");
    this.chatCompletionsUri = buildChatUri(config);
    warnIfApiKeyOverPlainHttp(config.getEndpoint(), this.apiKey);
  }

  private static URI buildChatUri(AzureOpenAiConfiguration config) {
    String root = StringUtils.stripEnd(config.getEndpoint(), "/");
    String deployment = config.getDeploymentName();
    String version = URLEncoder.encode(config.getApiVersion(), StandardCharsets.UTF_8)
        .replace("+", "%20");
    String url =
        root + "/openai/deployments/" + deployment + "/chat/completions?api-version=" + version;
    return URI.create(url);
  }

  /**
   * Builds the HTTP request for the Azure OpenAI chat completions API.
   *
   * @param request the LLM execution request
   * @return the configured HTTP request
   */
  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    return HttpRequest.newBuilder(chatCompletionsUri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .header("api-key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(request)))
        .build();
  }

  /**
   * Validates the Azure OpenAI chat completions payload and extracts assistant text plus
   * {@code usage} ({@code usage.prompt_tokens}, {@code usage.completion_tokens},
   * {@code usage.prompt_tokens_details.cached_tokens} when present) and root {@code model} for
   * {@link LlmExecutionResponse#modelUsed()}.
   *
   * @param json the raw JSON response from Azure OpenAI
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when the
   * {@code usage} block is absent
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected LlmExecutionResponse validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be blank"));
    LOG.log(System.Logger.Level.DEBUG, "azure-openai response body (full) body={0}", json);
    String truncatedJson = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(json);
    ChatCompletionsResponse dto = objectMapper.readValue(json, ChatCompletionsResponse.class);
    validateResponse(truncatedJson, dto);

    ChatChoice firstChoice = retrieveFirstChoice(truncatedJson, dto);
    ChatMessage message = firstChoice.message();
    String rawContent = message == null ? null : message.content();
    String content = Validate.notBlank(rawContent, () -> new
        LlmInvocationException(
        "azure-openai response first choice content is blank for deployment %s: %s".formatted(
            deploymentName, truncatedJson)));
    return new LlmExecutionResponse(
        LlmClient.stripCodeFence(content.strip()),
        StringUtils.trimToNull(dto.model()),
        ChatCompletionsApiSupport.toTokenUsageReport(dto.usage()));
  }

  private ChatChoice retrieveFirstChoice(String truncatedJson, ChatCompletionsResponse dto) {
    List<ChatChoice> choices = Validate.notEmpty(dto.choices(), () -> new
        LlmInvocationException(
        "azure-openai response choices are empty for deployment %s: %s".formatted(deploymentName,
            truncatedJson)));
    return Validate.notNull(choices.get(0), () -> new
        LlmInvocationException(
        "azure-openai first choice is null for deployment %s: %s".formatted(deploymentName,
            truncatedJson)));
  }

  private static void validateResponse(String truncatedJson, ChatCompletionsResponse dto) {
    Validate.notNull(dto,
        () -> new LlmInvocationException(
            "azure-openai response deserialized to null: %s".formatted(truncatedJson)));
    Validate.isTrue(dto.error() == null || StringUtils.isBlank(dto.error().message()),
        () -> new LlmInvocationException(
            "azure-openai error: %s".formatted(dto.error().message())));
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    ChatCompletionsRequest body = ChatCompletionsApiSupport.buildRequest(
        deploymentName, request.systemPrompt(), request.userInput(), null);
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize azure-openai request for deployment %s".formatted(deploymentName),
          e);
    }
  }
}
