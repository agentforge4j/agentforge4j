package com.agentforge4j.llm.api;

/**
 * Token usage reported by an LLM provider for a single invocation.
 * <p>
 * A {@code null} reference to this record means the provider returned no usage data. Individual
 * fields may also be {@code null} when the provider omits that count.
 */
public record TokenUsageReport(
    Integer inputTokens,
    Integer outputTokens,
    Integer cachedInputTokens,
    Integer cacheWriteTokens) {

}
