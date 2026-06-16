// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.api;

/**
 * Token usage reported by an LLM provider for a single invocation.
 * <p>
 * A {@code null} reference to this record on {@link LlmExecutionResponse#tokenUsage()} means the
 * provider returned no usage block at all for this call. Individual fields may be {@code null}
 * when the usage block is present but the provider omitted that count.
 *
 * @param inputTokens        non-cached input tokens when reported; {@code null} if absent
 * @param outputTokens       generated output tokens when reported; {@code null} if absent
 * @param cachedInputTokens  tokens served from a provider prompt cache when reported; populated
 *                           only by providers that expose prompt-cache accounting (for example
 *                           Anthropic Claude, Bedrock InvokeModel Anthropic, and sometimes OpenAI);
 *                           {@code null} if the provider did not report it for this call
 * @param cacheWriteTokens   tokens written into a provider prompt cache when reported; same
 *                           provider scope as {@code cachedInputTokens}; {@code null} if the
 *                           provider did not report it for this call
 */
public record TokenUsageReport(
    Integer inputTokens,
    Integer outputTokens,
    Integer cachedInputTokens,
    Integer cacheWriteTokens) {

}
