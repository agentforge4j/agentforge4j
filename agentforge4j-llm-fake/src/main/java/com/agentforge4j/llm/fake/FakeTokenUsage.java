package com.agentforge4j.llm.fake;

/**
 * Script-specified token usage for one response, mirroring {@link com.agentforge4j.llm.api.TokenUsageReport} exactly:
 * four nullable counts, no total. A {@code null} field means "not reported" for that count.
 *
 * @param inputTokens       non-cached input tokens, or {@code null}
 * @param outputTokens      generated output tokens, or {@code null}
 * @param cachedInputTokens prompt-cache input tokens, or {@code null}
 * @param cacheWriteTokens  prompt-cache write tokens, or {@code null}
 */
public record FakeTokenUsage(
    Integer inputTokens,
    Integer outputTokens,
    Integer cachedInputTokens,
    Integer cacheWriteTokens) {

}
