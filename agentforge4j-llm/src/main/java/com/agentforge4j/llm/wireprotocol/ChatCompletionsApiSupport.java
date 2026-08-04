// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.agentforge4j.llm.api.TokenUsageReport;
import java.util.List;

/**
 * Shared request-building and usage-mapping logic for OpenAI-style chat-completions API clients:
 * Azure OpenAI, Mistral and vLLM.
 * <p>
 * All three tolerate unrecognized response fields. That is a property of the shared DTOs
 * themselves &mdash; they are annotated {@code @JsonIgnoreProperties(ignoreUnknown = true)} &mdash;
 * rather than of the {@code ObjectMapper} the embedding application supplies, so a provider adding
 * a response field cannot break parsing on any host. See {@code ADR-0037}.
 * <p>
 * Error handling and choice/content extraction are deliberately <b>not</b> centralized here:
 * Azure embeds provider-specific context (the deployment name) in failure messages that Mistral
 * and vLLM do not, and vLLM performs no API-error check at all. Only the pieces that are
 * identical across the three &mdash; the request/response wire shapes and the
 * usage-to-{@link TokenUsageReport} mapping &mdash; are shared.
 */
public final class ChatCompletionsApiSupport {

  private ChatCompletionsApiSupport() {}

  /**
   * Builds the chat-completions request body for a system/user turn.
   *
   * @param model  the resolved model identifier
   * @param systemPrompt the system prompt
   * @param userInput    the user input
   * @param stream       whether to stream the response, or {@code null} to omit the field
   *
   * @return the request body, ready to serialize
   */
  public static ChatCompletionsRequest buildRequest(String model, String systemPrompt,
      String userInput, Boolean stream) {
    return new ChatCompletionsRequest(
        model,
        List.of(
            new ChatMessage(InputRole.SYSTEM, systemPrompt),
            new ChatMessage(InputRole.USER, userInput)),
        stream);
  }

  /**
   * Maps a chat-completions {@code usage} block to a provider-neutral {@link TokenUsageReport}.
   *
   * @param usage the raw usage block, or {@code null} when the provider omitted it
   *
   * @return the mapped report, or {@code null} when {@code usage} is {@code null}
   */
  public static TokenUsageReport toTokenUsageReport(ChatCompletionsUsage usage) {
    if (usage == null) {
      return null;
    }
    Integer cachedInputTokens = null;
    CachedTokensDetails details = usage.promptTokensDetails();
    if (details != null) {
      cachedInputTokens = details.cachedTokens();
    }
    return new TokenUsageReport(
        usage.promptTokens(),
        usage.completionTokens(),
        cachedInputTokens,
        null);
  }
}
