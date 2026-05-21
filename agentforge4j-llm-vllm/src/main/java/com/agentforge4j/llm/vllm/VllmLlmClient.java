package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.llm.vllm.dto.InputRole;
import com.agentforge4j.llm.vllm.dto.VllmChoice;
import com.agentforge4j.llm.vllm.dto.VllmMessage;
import com.agentforge4j.llm.vllm.dto.VllmPromptTokensDetails;
import com.agentforge4j.llm.vllm.dto.VllmRequest;
import com.agentforge4j.llm.vllm.dto.VllmResponse;
import com.agentforge4j.llm.vllm.dto.VllmUsage;
import com.agentforge4j.util.Validate;
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
 * Sends requests to a vLLM server using the chat completions API.
 */
public final class VllmLlmClient extends AbstractHttpLlmClient {

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
    VllmResponse response = objectMapper.readValue(json, VllmResponse.class);
    List<VllmChoice> choices = response == null ? null : response.choices();
    Validate.notEmpty(choices, () -> new LlmInvocationException(
        "vLLM response choices are empty: %s".formatted(json)));

    String content = choices.get(0) == null || choices.get(0).message() == null
        ? null
        : choices.get(0).message().content();
    Validate.notBlank(content, () -> new LlmInvocationException(
        "vLLM response first choice content is blank: %s".formatted(json)));

    VllmUsage usage = response == null ? null : response.usage();
    String modelUsed = response == null ? null : response.model();
    return new LlmExecutionResponse(
        LlmClient.stripCodeFence(content.strip()),
        StringUtils.trimToNull(modelUsed),
        toTokenUsageReport(usage));
  }

  private static TokenUsageReport toTokenUsageReport(VllmUsage usage) {
    if (usage == null) {
      return null;
    }
    Integer cachedInputTokens = null;
    VllmPromptTokensDetails details = usage.promptTokensDetails();
    if (details != null) {
      cachedInputTokens = details.cachedTokens();
    }
    return new TokenUsageReport(
        usage.promptTokens(),
        usage.completionTokens(),
        cachedInputTokens,
        null);
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    String model = StringUtils.defaultIfBlank(request.model(), getDefaultModel());
    VllmRequest body = new VllmRequest(
        model,
        List.of(
            new VllmMessage(InputRole.SYSTEM, request.systemPrompt()),
            new VllmMessage(InputRole.USER, request.userInput())),
        false);
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize vLLM request for model %s".formatted(model), e);
    }
  }
}
