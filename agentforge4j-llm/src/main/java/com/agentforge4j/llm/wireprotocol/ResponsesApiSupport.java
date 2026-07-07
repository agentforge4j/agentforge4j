// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared parsing, validation, and request-building logic for OpenAI-style Responses API clients.
 * <p>
 * {@code OpenAiLlmClient} and {@code OpenAiCompatibleLlmClient} both talk to the same wire
 * protocol (fixed OpenAI endpoint vs. a configurable OpenAI-compatible endpoint); this class holds
 * the protocol logic they share, parameterized only by the human-readable provider label used in
 * exception messages (e.g. {@code "OpenAI"} vs {@code "openai-compatible"}).
 */
public final class ResponsesApiSupport {

  private ResponsesApiSupport() {}

  /**
   * Builds the Responses API request body for a system/user turn.
   *
   * @param model           the resolved model identifier
   * @param systemPrompt    the system prompt
   * @param userInput       the user input
   * @param maxOutputTokens optional output token budget, or {@code null} to omit it
   *
   * @return the request body, ready to serialize
   */
  public static ResponsesRequest buildRequest(String model, String systemPrompt, String userInput,
      Integer maxOutputTokens) {
    return new ResponsesRequest(
        model,
        List.of(
            new ResponsesInputItem(InputRole.SYSTEM, systemPrompt),
            new ResponsesInputItem(InputRole.USER, userInput)),
        maxOutputTokens);
  }

  /**
   * Parses a Responses API payload and extracts assistant text plus token usage.
   *
   * @param objectMapper  the JSON mapper for deserialization
   * @param json          the raw JSON response body
   * @param truncatedJson {@code json} already truncated for safe embedding in exception
   *                      messages (see {@code LlmHttpErrorBodyTruncate})
   * @param providerLabel human-readable provider name used in exception messages (e.g.
   *                      {@code "OpenAI"}, {@code "openai-compatible"})
   *
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when the
   * {@code usage} block is absent
   *
   * @throws IOException if the body cannot be deserialized
   */
  public static LlmExecutionResponse parseResponse(ObjectMapper objectMapper, String json,
      String truncatedJson, String providerLabel) throws IOException {
    ResponsesResponse dto = objectMapper.readValue(json, ResponsesResponse.class);
    validateApiError(dto, truncatedJson, providerLabel);
    String text = LlmClient.stripCodeFence(extractAssistantText(dto)
        .orElseThrow(() -> new LlmInvocationException(
            "%s response missing assistant output_text in message output item: %s".formatted(
                providerLabel, truncatedJson)))
        .strip());
    return new LlmExecutionResponse(
        text,
        StringUtils.trimToNull(dto.model()),
        toTokenUsageReport(dto.usage()));
  }

  private static void validateApiError(ResponsesResponse dto, String truncatedJson,
      String providerLabel) {
    Validate.notNull(dto,
        () -> new LlmInvocationException(
            "%s response deserialized to null: %s".formatted(providerLabel, truncatedJson)));
    Validate.isTrue(dto.error() == null || StringUtils.isBlank(dto.error().message()),
        () -> new LlmInvocationException(
            "%s error: %s".formatted(providerLabel, dto.error().message())));
    Validate.isTrue(dto.output() != null && !dto.output().isEmpty(),
        () -> new LlmInvocationException(
            "%s response missing or empty output: %s".formatted(providerLabel, truncatedJson)));
  }

  private static Optional<String> extractAssistantText(ResponsesResponse dto) {
    return dto.output().stream()
        .filter(item -> item != null && "message".equalsIgnoreCase(item.type()))
        .map(ResponsesOutputItem::content)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .filter(content -> content != null && "output_text".equalsIgnoreCase(content.type()))
        .map(ResponsesContentItem::text)
        .filter(StringUtils::isNotBlank)
        .findFirst();
  }

  private static TokenUsageReport toTokenUsageReport(ResponsesUsage usage) {
    if (usage == null) {
      return null;
    }
    Integer cachedInputTokens = null;
    CachedTokensDetails details = usage.inputTokensDetails();
    if (details != null) {
      cachedInputTokens = details.cachedTokens();
    }
    return new TokenUsageReport(
        usage.inputTokens(),
        usage.outputTokens(),
        cachedInputTokens,
        null);
  }
}
