/**
 * Public LLM invocation contract shared by runtime callers and provider adapters.
 * <p>
 * Key types:
 * <ul>
 *   <li>{@link com.agentforge4j.llm.api.LlmClient} — executes requests for one provider id</li>
 *   <li>{@link com.agentforge4j.llm.api.LlmExecutionRequest} — immutable invocation parameters</li>
 *   <li>{@link com.agentforge4j.llm.api.LlmInvocationException} — failures from clients or transport</li>
 *   <li>{@link com.agentforge4j.llm.api.LlmRetryPolicy} — optional retry settings exposed by clients</li>
 *   <li>{@link com.agentforge4j.llm.api.TokenUsageReport} — provider token usage for an invocation</li>
 *   <li>{@link com.agentforge4j.llm.api.PromptLayerBoundaries} — stable-prefix layer byte boundaries</li>
 * </ul>
 */
package com.agentforge4j.llm.api;
