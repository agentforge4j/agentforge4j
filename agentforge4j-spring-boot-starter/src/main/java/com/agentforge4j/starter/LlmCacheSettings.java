package com.agentforge4j.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator on/off switch for prompt-layer boundary computation in
 * {@link com.agentforge4j.runtime.llm.AgentInvoker}.
 *
 * <p>When {@link #enabled()} is {@code true}, the runtime attaches {@link
 * com.agentforge4j.llm.api.PromptLayerBoundaries} to each
 * {@link com.agentforge4j.llm.api.LlmExecutionRequest} so explicit-marker providers can emit cache
 * breakpoints. When {@code false}, boundaries stay {@code null} and request bodies match
 * pre-caching assembly.
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.cache")
public record LlmCacheSettings(
    boolean enabled
) {

}
