// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.wireprotocol;

import com.agentforge4j.llm.api.TokenUsageReport;
import java.util.List;

/**
 * Shared request-building and usage-mapping logic for OpenAI-style chat-completions API clients
 * that are strict about unrecognized response fields: Azure OpenAI and Mistral.
 * <p>
 * vLLM is deliberately excluded: it is intentionally lenient about unknown JSON fields (its own
 * {@code dto} classes are annotated {@code @JsonIgnoreProperties(ignoreUnknown = true)} and it has
 * a test asserting that tolerance), while Azure and Mistral each have a test asserting the
 * opposite &mdash; a strict failure on unrecognized fields. Forcing both behaviors onto one shared
 * {@code @JsonIgnoreProperties}-annotated class is not possible without breaking one of them, so
 * vLLM keeps its own DTOs and request builder instead of using this class.
 * <p>
 * Error handling and choice/content extraction are also deliberately <b>not</b> centralized here
 * even for Azure/Mistral: Azure embeds provider-specific context (the deployment name) in failure
 * messages that Mistral does not. Only the pieces that are byte-for-byte identical between the two
 * &mdash; the request/response wire shapes and the usage-to-{@link TokenUsageReport} mapping
 * &mdash; are shared.
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
